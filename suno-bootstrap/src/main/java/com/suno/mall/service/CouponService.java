package com.suno.mall.service;

import com.suno.mall.common.BizException;
import com.suno.mall.common.ErrorCode;
import com.suno.mall.config.ResaleTransactional;
import com.suno.mall.entity.CouponEntity;
import com.suno.mall.entity.CouponUserEntity;
import com.suno.mall.entity.UserAccountEntity;
import com.suno.mall.dao.CouponRepository;
import com.suno.mall.dao.CouponUserRepository;
import com.suno.mall.dao.UserAccountRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * M2 优惠券：满减券（FIXED）。领取原子扣减券库存并受单人上限约束；
 * 核销与订单同事务（仅 UNUSED 未过期本人券可核销），取消订单时回滚为 UNUSED。
 */
@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUserRepository couponUserRepository;
    private final UserAccountRepository userAccountRepository;

    public CouponService(CouponRepository couponRepository, CouponUserRepository couponUserRepository,
                         UserAccountRepository userAccountRepository) {
        this.couponRepository = couponRepository;
        this.couponUserRepository = couponUserRepository;
        this.userAccountRepository = userAccountRepository;
    }

    /** 可领取列表：ACTIVE 且未过有效期，剩余量按截止时间升序 */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public List<Map<String, Object>> listClaimable() {
        return couponRepository.findByStatusAndEndTimeAfterOrderByEndTimeAsc("ACTIVE", LocalDateTime.now()).stream()
                .filter(coupon -> coupon.getRemainingCount() > 0)
                .map(CouponService::toCouponSummary)
                .toList();
    }

    @ResaleTransactional
    public Map<String, Object> claim(Long userId, Long couponId) {
        CouponEntity coupon = couponRepository.findById(couponId)
                .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
                .orElseThrow(() -> new BizException("优惠券不存在或已停用", ErrorCode.COUPON_NOT_FOUND));
        if (!coupon.getEndTime().isAfter(LocalDateTime.now()) || !coupon.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BizException("优惠券不在可领取时间内", ErrorCode.COUPON_NOT_FOUND);
        }
        if (couponRepository.consumeOneStock(couponId, "ACTIVE") == 0) {
            throw new BizException("优惠券已领完", ErrorCode.COUPON_OUT_OF_STOCK);
        }
        try {
            return persistClaimedCoupon(userId, coupon);
        } catch (RuntimeException failure) {
            // 领取失败（如超单人上限）回补券库存
            couponRepository.restoreOneStock(couponId);
            throw failure;
        }
    }

    private Map<String, Object> persistClaimedCoupon(Long userId, CouponEntity coupon) {
        int owned = couponUserRepository.countByUser_IdAndCoupon_Id(userId, coupon.getId());
        if (owned >= coupon.getPerUserLimit()) {
            throw new BizException("已达该券领取上限", ErrorCode.COUPON_CLAIM_LIMIT);
        }
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在: " + userId, ErrorCode.PARAM_INVALID));
        CouponUserEntity mine = new CouponUserEntity();
        mine.setUser(user);
        mine.setCoupon(coupon);
        mine.setStatus("UNUSED");
        mine.setExpireAt(coupon.getEndTime());
        couponUserRepository.save(mine);
        Map<String, Object> result = new HashMap<>(toCouponSummary(coupon));
        result.put("couponUserId", mine.getId());
        result.put("status", mine.getStatus());
        result.put("expireAt", mine.getExpireAt());
        return result;
    }

    /** 我的券：status 过滤（UNUSED/USED），未用在前、按过期时间升序 */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> listMine(Long userId, String status, Integer page, Integer size) {
        String safeStatus = status == null || status.isBlank() ? null : status;
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        var result = couponUserRepository.pageMine(userId, safeStatus,
                PageRequest.of(safePage, safeSize, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.asc("expireAt"))));
        List<Map<String, Object>> content = result.getContent().stream().map(mine -> {
            Map<String, Object> row = new HashMap<>(toCouponSummary(mine.getCoupon()));
            row.put("couponUserId", mine.getId());
            row.put("status", mine.getStatus());
            row.put("expireAt", mine.getExpireAt());
            row.put("usedAt", mine.getUsedAt());
            return row;
        }).toList();
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        payload.put("totalElements", result.getTotalElements());
        payload.put("totalPages", result.getTotalPages());
        payload.put("page", safePage);
        payload.put("size", safeSize);
        return payload;
    }

    /**
     * 结算预览：判断某券对给定订单金额可用，返回抵扣额。不修改任何状态。
     */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> previewRedeem(Long userId, Long couponUserId, BigDecimal orderAmount) {
        CouponUserEntity mine = requireRedeemable(userId, couponUserId);
        BigDecimal discount = computeDiscount(mine.getCoupon(), orderAmount);
        Map<String, Object> preview = new HashMap<>();
        preview.put("couponUserId", mine.getId());
        preview.put("discountAmount", discount);
        preview.put("payableAmount", orderAmount.subtract(discount));
        return preview;
    }

    /**
     * 核销（与订单创建同一事务）：CAS 式校验后标记 USED 并绑定订单。
     * 幂等：同一券重复核销会被 countRedeemable=0 拒绝。
     */
    @ResaleTransactional
    public Map<String, Object> redeem(Long userId, Long couponUserId, BigDecimal orderAmount, Long orderId) {
        CouponUserEntity mine = requireRedeemable(userId, couponUserId);
        if (couponUserRepository.countRedeemable(couponUserId, userId, LocalDateTime.now()) == 0) {
            throw new BizException("优惠券不可用（可能已被使用）", ErrorCode.COUPON_NOT_REDEEMABLE);
        }
        BigDecimal discount = computeDiscount(mine.getCoupon(), orderAmount);
        mine.setStatus("USED");
        mine.setUsedAt(LocalDateTime.now());
        mine.setOrderId(orderId);
        couponUserRepository.save(mine);
        return Map.of("couponUserId", couponUserId, "discountAmount", discount,
                "payableAmount", orderAmount.subtract(discount));
    }

    /** 取消订单时回滚券为 UNUSED（幂等：仅绑定该订单的 USED 券回滚） */
    @ResaleTransactional
    public boolean releaseForOrder(Long orderId) {
        var bound = couponUserRepository.findByOrderId(orderId);
        if (bound.isEmpty()) {
            return false;
        }
        CouponUserEntity mine = bound.get();
        if (!"USED".equals(mine.getStatus())) {
            return false;
        }
        mine.setStatus("UNUSED");
        mine.setUsedAt(null);
        mine.setOrderId(null);
        couponUserRepository.save(mine);
        return true;
    }

    private CouponUserEntity requireRedeemable(Long userId, Long couponUserId) {
        CouponUserEntity mine = couponUserRepository.findByIdAndUser_Id(couponUserId, userId)
                .orElseThrow(() -> new BizException("优惠券不存在", ErrorCode.COUPON_NOT_FOUND));
        if (!"UNUSED".equals(mine.getStatus())) {
            throw new BizException("优惠券已使用", ErrorCode.COUPON_NOT_REDEEMABLE);
        }
        if (!mine.getExpireAt().isAfter(LocalDateTime.now())) {
            throw new BizException("优惠券已过期", ErrorCode.COUPON_NOT_REDEEMABLE);
        }
        return mine;
    }

    /** 满减券抵扣：达到门槛抵 discountValue，否则拒绝；抵扣不超过订单金额 */
    private BigDecimal computeDiscount(CouponEntity coupon, BigDecimal orderAmount) {
        BigDecimal threshold = coupon.getMinOrderAmount() == null ? BigDecimal.ZERO : coupon.getMinOrderAmount();
        if (orderAmount.compareTo(threshold) < 0) {
            throw new BizException("订单金额未满足用券门槛", ErrorCode.COUPON_MIN_AMOUNT_NOT_MET);
        }
        BigDecimal discount = coupon.getDiscountValue().min(orderAmount);
        return discount.max(BigDecimal.ZERO);
    }

    // ==================== M3 管理端 ====================

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public List<Map<String, Object>> adminListCoupons(String status) {
        return couponRepository.findAll().stream()
                .filter(coupon -> status == null || status.isBlank() || status.equals(coupon.getStatus()))
                .sorted(java.util.Comparator.comparing(CouponEntity::getId).reversed())
                .map(coupon -> {
                    Map<String, Object> row = new HashMap<>(toCouponSummary(coupon));
                    row.put("totalCount", coupon.getTotalCount());
                    row.put("status", coupon.getStatus());
                    return row;
                })
                .toList();
    }

    @ResaleTransactional
    public Map<String, Object> adminCreateCoupon(String name, BigDecimal minOrderAmount, BigDecimal discountValue,
                                                 int totalCount, int perUserLimit, int effectiveDays) {
        if (name == null || name.isBlank() || discountValue == null
                || discountValue.compareTo(BigDecimal.ZERO) <= 0 || totalCount <= 0
                || perUserLimit <= 0 || effectiveDays <= 0 || effectiveDays > 365) {
            throw new BizException("优惠券参数不合法", ErrorCode.PARAM_INVALID);
        }
        if (minOrderAmount != null && minOrderAmount.compareTo(discountValue) < 0) {
            throw new BizException("用券门槛不能低于抵扣金额", ErrorCode.PARAM_INVALID);
        }
        CouponEntity coupon = new CouponEntity();
        coupon.setName(name.trim());
        coupon.setCouponType("FIXED");
        coupon.setDiscountValue(discountValue);
        coupon.setMinOrderAmount(minOrderAmount);
        coupon.setTotalCount(totalCount);
        coupon.setRemainingCount(totalCount);
        coupon.setPerUserLimit(perUserLimit);
        coupon.setStartTime(LocalDateTime.now());
        coupon.setEndTime(LocalDateTime.now().plusDays(effectiveDays));
        coupon.setStatus("ACTIVE");
        couponRepository.save(coupon);
        Map<String, Object> row = new HashMap<>(toCouponSummary(coupon));
        row.put("totalCount", coupon.getTotalCount());
        row.put("status", coupon.getStatus());
        return row;
    }

    @ResaleTransactional
    public Map<String, Object> adminDisableCoupon(Long couponId) {
        CouponEntity coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BizException("优惠券不存在", ErrorCode.COUPON_NOT_FOUND));
        if (!"ACTIVE".equals(coupon.getStatus())) {
            return Map.of("couponId", couponId, "status", coupon.getStatus(), "idempotentReplay", true);
        }
        coupon.setStatus("DISABLED");
        couponRepository.save(coupon);
        return Map.of("couponId", couponId, "status", "DISABLED");
    }

    private static Map<String, Object> toCouponSummary(CouponEntity coupon) {
        Map<String, Object> row = new HashMap<>();
        row.put("couponId", coupon.getId());
        row.put("name", coupon.getName());
        row.put("couponType", coupon.getCouponType());
        row.put("discountValue", coupon.getDiscountValue());
        row.put("minOrderAmount", coupon.getMinOrderAmount());
        row.put("remainingCount", coupon.getRemainingCount());
        row.put("perUserLimit", coupon.getPerUserLimit());
        row.put("startTime", coupon.getStartTime());
        row.put("endTime", coupon.getEndTime());
        return row;
    }
}
