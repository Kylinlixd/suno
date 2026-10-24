package com.suno.mall.service;

import com.suno.mall.common.BizException;
import com.suno.mall.common.ErrorCode;
import com.suno.mall.config.ResaleTransactional;
import com.suno.mall.entity.CartItemEntity;
import com.suno.mall.entity.ResaleListingEntity;
import com.suno.mall.entity.UserAccountEntity;
import com.suno.mall.dao.CartItemRepository;
import com.suno.mall.dao.ResaleListingRepository;
import com.suno.mall.dao.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * M2 购物车。二手单件模型：数量恒为 1；加入前校验在售与库存，
 * 结算时仍以下单接口的原子库存预留为准（购物车不锁库存）。
 */
@Service
public class CartService {

    private static final String LISTING_STATUS_ON_SHELF = "ON_SHELF";

    private final CartItemRepository cartItemRepository;
    private final ResaleListingRepository resaleListingRepository;
    private final UserAccountRepository userAccountRepository;

    public CartService(CartItemRepository cartItemRepository,
                       ResaleListingRepository resaleListingRepository,
                       UserAccountRepository userAccountRepository) {
        this.cartItemRepository = cartItemRepository;
        this.resaleListingRepository = resaleListingRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> listCart(Long userId) {
        List<CartItemEntity> items = cartItemRepository.findWithDetailsByUser_Id(userId);
        List<Map<String, Object>> content = items.stream().map(item -> {
            ResaleListingEntity listing = item.getListing();
            Map<String, Object> row = new HashMap<>();
            row.put("cartItemId", item.getId());
            row.put("listingId", listing.getId());
            row.put("brand", listing.getProduct().getBrand());
            row.put("model", listing.getProduct().getModel());
            row.put("grade", listing.getProduct().getRecycleGrade());
            row.put("salePrice", listing.getSalePrice());
            row.put("stock", listing.getStock());
            row.put("listingStatus", listing.getStatus());
            row.put("image", listing.getProduct().getImageUrl());
            row.put("addedAt", item.getCreatedAt());
            // 下单时真正校验库存/状态；此处仅给出可结算标记供前端置灰
            row.put("orderable", LISTING_STATUS_ON_SHELF.equals(listing.getStatus()) && listing.getStock() > 0);
            return row;
        }).toList();
        return Map.of("content", content, "totalElements", content.size());
    }

    @ResaleTransactional
    public Map<String, Object> addToCart(Long userId, Long listingId) {
        ResaleListingEntity listing = resaleListingRepository.findWithDetailsById(listingId)
                .orElseThrow(() -> new BizException("商品不存在: " + listingId, ErrorCode.LISTING_NOT_FOUND));
        if (!LISTING_STATUS_ON_SHELF.equals(listing.getStatus()) || listing.getStock() <= 0) {
            throw new BizException("商品已下架或售罄，无法加入购物车", ErrorCode.ORDER_LISTING_UNAVAILABLE);
        }
        var existing = cartItemRepository.findByUser_IdAndListing_Id(userId, listingId);
        if (existing.isPresent()) {
            return Map.of("cartItemId", existing.get().getId(),
                    "listingId", listingId, "added", true, "idempotentReplay", true);
        }
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在: " + userId, ErrorCode.PARAM_INVALID));
        CartItemEntity item = new CartItemEntity();
        item.setUser(user);
        item.setListing(listing);
        item.setCreatedAt(LocalDateTime.now());
        cartItemRepository.save(item);
        return Map.of("cartItemId", item.getId(), "listingId", listingId, "added", true, "idempotentReplay", false);
    }

    @ResaleTransactional
    public Map<String, Object> removeFromCart(Long userId, Long cartItemId) {
        CartItemEntity item = cartItemRepository.findByIdAndUser_Id(cartItemId, userId)
                .orElseThrow(() -> new BizException("购物车条目不存在", ErrorCode.CART_ITEM_NOT_FOUND));
        cartItemRepository.delete(item);
        return Map.of("cartItemId", cartItemId, "removed", true);
    }

    /** 下单成功后从购物车移除对应商品（幂等：不存在即忽略） */
    @ResaleTransactional
    public void removeListingFromCart(Long userId, Long listingId) {
        cartItemRepository.deleteByUser_IdAndListing_Id(userId, listingId);
    }
}
