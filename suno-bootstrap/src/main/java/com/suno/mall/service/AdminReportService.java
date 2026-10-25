package com.suno.mall.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * M3/P3 经营报表：聚合全部下推数据库（count/sum/group by 日历日），
 * 支持时间切片与上一周期环比；维度覆盖订单/GMV/回收/券核销/用户/漏斗。
 */
@Service
public class AdminReportService {

    private final com.suno.mall.dao.ResaleOrderRepository resaleOrderRepository;
    private final com.suno.mall.dao.RecycleOrderRepository recycleOrderRepository;
    private final com.suno.mall.dao.CouponUserRepository couponUserRepository;
    private final com.suno.mall.dao.UserAccountRepository userAccountRepository;

    public AdminReportService(com.suno.mall.dao.ResaleOrderRepository resaleOrderRepository,
                              com.suno.mall.dao.RecycleOrderRepository recycleOrderRepository,
                              com.suno.mall.dao.CouponUserRepository couponUserRepository,
                              com.suno.mall.dao.UserAccountRepository userAccountRepository) {
        this.resaleOrderRepository = resaleOrderRepository;
        this.recycleOrderRepository = recycleOrderRepository;
        this.couponUserRepository = couponUserRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> overview(Integer trendDays) {
        int days = trendDays == null || trendDays < 1 || trendDays > 90 ? 14 : trendDays;
        LocalDateTime windowStart = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        LocalDateTime previousStart = windowStart.minusDays(days);

        Map<String, Object> current = orderStats(windowStart, null);
        Map<String, Object> previous = orderStats(previousStart, windowStart);
        Map<String, Object> trend = orderTrend(windowStart, days);
        Map<String, Object> recycle = recycleStats(windowStart, null);
        Map<String, Object> coupon = couponStats(windowStart, null);
        Map<String, Object> users = userStats(windowStart, null);

        Map<String, Object> payload = new HashMap<>(current);
        payload.put("days", days);
        payload.put("trendDays", days);
        payload.put("trend", trend.get("trend"));
        payload.put("previous", previous);
        payload.put("delta", deltas(current, previous));
        payload.put("recycle", recycle);
        payload.put("coupons", coupon);
        payload.put("users", users);
        payload.put("funnel", funnel(current));
        return payload;
    }

    /** 汇总行 CSV（与 overview 同口径，供财务留档） */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public String exportCsv(Integer trendDays) {
        Map<String, Object> data = overview(trendDays);
        StringBuilder csv = new StringBuilder("metric,value\n");
        csv.append("days,").append(data.get("days")).append('\n');
        csv.append("totalOrders,").append(data.get("totalOrders")).append('\n');
        csv.append("paidOrders,").append(data.get("paidOrders")).append('\n');
        csv.append("completedOrders,").append(data.get("completedOrders")).append('\n');
        csv.append("refundedOrders,").append(data.get("refundedOrders")).append('\n');
        csv.append("refundRate,").append(data.get("refundRate")).append('\n');
        csv.append("gmv,").append(data.get("gmv")).append('\n');
        csv.append("refundedAmount,").append(data.get("refundedAmount")).append('\n');
        csv.append("avgPaidAmount,").append(data.get("avgPaidAmount")).append('\n');
        Map<?, ?> recycle = (Map<?, ?>) data.get("recycle");
        csv.append("recycleOrders,").append(recycle.get("total")).append('\n');
        csv.append("recycleListedRate,").append(recycle.get("listedRate")).append('\n');
        Map<?, ?> coupons = (Map<?, ?>) data.get("coupons");
        csv.append("couponClaimed,").append(coupons.get("claimed")).append('\n');
        csv.append("couponRedeemed,").append(coupons.get("redeemed")).append('\n');
        csv.append("couponRedeemRate,").append(coupons.get("redeemRate")).append('\n');
        Map<?, ?> users = (Map<?, ?>) data.get("users");
        csv.append("totalUsers,").append(users.get("total")).append('\n');
        csv.append("activeBuyers,").append(users.get("activeBuyers")).append('\n');
        return csv.toString();
    }

    // ==================== 订单/GMV（窗口 [start, end)，end 为 null 表示开区间） ====================

    private Map<String, Object> orderStats(LocalDateTime start, LocalDateTime end) {
        Long totalOrders = resaleOrderRepository.countInWindow(start, end);
        Long paidOrders = resaleOrderRepository.countByPayStatusInWindow(List.of("PAID", "REFUNDED"), start, end);
        Long completedOrders = resaleOrderRepository.countByFulfillStatusInWindow("COMPLETED", start, end);
        Long refundedOrders = resaleOrderRepository.countByPayStatusInWindow(List.of("REFUNDED"), start, end);
        Long cancelledOrders = resaleOrderRepository.countByFulfillStatusInWindow("CANCELLED", start, end)
                + resaleOrderRepository.countByFulfillStatusInWindow("AUTO_CLOSED", start, end);
        BigDecimal gmv = nvl(resaleOrderRepository.sumPayableInWindow(List.of("PAID", "REFUNDED"), start, end));
        BigDecimal refundedAmount = nvl(resaleOrderRepository.sumPayableInWindow(List.of("REFUNDED"), start, end));
        BigDecimal avgPaidAmount = paidOrders == 0 ? BigDecimal.ZERO
                : gmv.subtract(refundedAmount).divide(BigDecimal.valueOf(paidOrders), 2, RoundingMode.HALF_UP);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOrders", totalOrders);
        stats.put("paidOrders", paidOrders);
        stats.put("completedOrders", completedOrders);
        stats.put("refundedOrders", refundedOrders);
        stats.put("cancelledOrders", cancelledOrders);
        stats.put("refundRate", rate(refundedOrders, totalOrders));
        stats.put("gmv", gmv);
        stats.put("refundedAmount", refundedAmount);
        stats.put("avgPaidAmount", avgPaidAmount);
        stats.put("payRate", rate(paidOrders, totalOrders));
        return stats;
    }

    private Map<String, Object> orderTrend(LocalDateTime start, int days) {
        List<Object[]> rows = resaleOrderRepository.dailySeries(start);
        Map<LocalDate, long[]> buckets = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate day = toDay(row[0]);
            long total = ((Number) row[1]).longValue();
            long paid = row.length > 2 && row[2] != null ? ((Number) row[2]).longValue() : 0;
            long[] bucket = buckets.computeIfAbsent(day, ignored -> new long[2]);
            bucket[0] += total;
            bucket[1] += paid;
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int offset = days - 1; offset >= 0; offset--) {
            LocalDate day = LocalDate.now().minusDays(offset);
            long[] bucket = buckets.getOrDefault(day, new long[2]);
            Map<String, Object> point = new HashMap<>();
            point.put("date", day.toString());
            point.put("orders", bucket[0]);
            point.put("paidOrders", bucket[1]);
            trend.add(point);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("trend", trend);
        return result;
    }

    private static Map<String, Object> funnel(Map<String, Object> stats) {
        Map<String, Object> funnel = new HashMap<>();
        funnel.put("ordered", stats.get("totalOrders"));
        funnel.put("paid", stats.get("paidOrders"));
        funnel.put("completed", stats.get("completedOrders"));
        funnel.put("refunded", stats.get("refundedOrders"));
        return funnel;
    }

    private static Map<String, Object> deltas(Map<String, Object> current, Map<String, Object> previous) {
        Map<String, Object> delta = new HashMap<>();
        for (String key : List.of("totalOrders", "paidOrders", "gmv", "refundedOrders", "refundRate", "avgPaidAmount")) {
            Object currentValue = current.get(key);
            Object previousValue = previous.get(key);
            if (currentValue instanceof Number currentNumber && previousValue instanceof Number previousNumber) {
                double previousDouble = previousNumber.doubleValue();
                double change = previousDouble == 0
                        ? (currentNumber.doubleValue() == 0 ? 0 : 100)
                        : (currentNumber.doubleValue() - previousDouble) / previousDouble * 100;
                delta.put(key, BigDecimal.valueOf(change).setScale(1, RoundingMode.HALF_UP).doubleValue());
            }
        }
        return delta;
    }

    // ==================== 回收 / 券 / 用户 ====================

    private Map<String, Object> recycleStats(LocalDateTime start, LocalDateTime end) {
        Long total = recycleOrderRepository.countInWindow(start, end);
        Long listed = recycleOrderRepository.countByStatusInWindow("LISTED", start, end);
        BigDecimal estimatedTotal = nvl(recycleOrderRepository.sumEstimatedPriceInWindow(start, end));
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("listed", listed);
        stats.put("listedRate", rate(listed, total));
        stats.put("estimatedTotal", estimatedTotal);
        return stats;
    }

    private Map<String, Object> couponStats(LocalDateTime start, LocalDateTime end) {
        Long claimed = couponUserRepository.countInWindow(start, end);
        Long redeemed = couponUserRepository.countByStatusInWindow("USED", start, end);
        Map<String, Object> stats = new HashMap<>();
        stats.put("claimed", claimed);
        stats.put("redeemed", redeemed);
        stats.put("redeemRate", rate(redeemed, claimed));
        return stats;
    }

    private Map<String, Object> userStats(LocalDateTime start, LocalDateTime end) {
        Long total = userAccountRepository.count();
        Long activeBuyers = resaleOrderRepository.countDistinctBuyersInWindow(start, end);
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("activeBuyers", activeBuyers);
        return stats;
    }

    // ==================== helpers ====================

    private static LocalDate toDay(Object value) {
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static double rate(long part, long total) {
        if (total == 0) {
            return 0;
        }
        return BigDecimal.valueOf(part * 100.0 / total).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
