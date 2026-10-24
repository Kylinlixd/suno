package com.suno.mall.service;

import com.suno.mall.common.BizException;
import com.suno.mall.common.ErrorCode;
import com.suno.mall.config.ResaleTransactional;
import com.suno.mall.entity.ResaleListingEntity;
import com.suno.mall.entity.ResaleOrderEntity;
import com.suno.mall.dao.ResaleListingRepository;
import com.suno.mall.dao.ResaleOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * M3 运营后台：商品与订单管理查询/动作（全部要求 ADMIN 路由，由 SecurityConfig 保证）。
 */
@Service
public class AdminCatalogService {

    private final ResaleListingRepository resaleListingRepository;
    private final ResaleOrderRepository resaleOrderRepository;
    private final com.suno.mall.dao.ProductImageRepository productImageRepository;
    private final AuditLogService auditLogService;

    public AdminCatalogService(ResaleListingRepository resaleListingRepository,
                               ResaleOrderRepository resaleOrderRepository,
                               com.suno.mall.dao.ProductImageRepository productImageRepository,
                               AuditLogService auditLogService) {
        this.resaleListingRepository = resaleListingRepository;
        this.resaleOrderRepository = resaleOrderRepository;
        this.productImageRepository = productImageRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> pageListings(String status, Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        Page<ResaleListingEntity> result = (status == null || status.isBlank())
                ? resaleListingRepository.findAllWithDetails(PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")))
                : resaleListingRepository.findByStatusWithDetailsPaged(status, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return listingPage(result, safePage, safeSize);
    }

    @ResaleTransactional
    public Map<String, Object> adjustPrice(Long listingId, BigDecimal salePrice) {
        ResaleListingEntity listing = requireListing(listingId);
        BigDecimal previous = listing.getSalePrice();
        listing.setSalePrice(salePrice);
        listing.setUpdatedAt(LocalDateTime.now());
        resaleListingRepository.save(listing);
        auditLogService.logAction("RESALE_LISTING_PRICE", "RESALE_LISTING", String.valueOf(listingId),
                "from=" + previous + ",to=" + salePrice);
        return Map.of("listingId", listingId, "salePrice", salePrice, "previousPrice", previous);
    }

    /** 上架/下架：下架要求 ON_SHELF，上架要求 OFF_SHELF/SOLD_OUT 之外的手工下架态 */
    @ResaleTransactional
    public Map<String, Object> changeShelf(Long listingId, boolean onShelf) {
        ResaleListingEntity listing = requireListing(listingId);
        String target = onShelf ? "ON_SHELF" : "OFF_SHELF";
        if (target.equals(listing.getStatus())) {
            return Map.of("listingId", listingId, "status", listing.getStatus(), "idempotentReplay", true);
        }
        if (onShelf && listing.getStock() <= 0) {
            throw new BizException("库存为 0 不可上架", ErrorCode.ORDER_STOCK_INSUFFICIENT);
        }
        if (!onShelf && !"ON_SHELF".equals(listing.getStatus())) {
            throw new BizException("仅上架商品可下架", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        listing.setStatus(target);
        listing.setUpdatedAt(LocalDateTime.now());
        resaleListingRepository.save(listing);
        auditLogService.logAction(onShelf ? "RESALE_LISTING_ON_SHELF" : "RESALE_LISTING_OFF_SHELF",
                "RESALE_LISTING", String.valueOf(listingId), "status=" + target);
        return Map.of("listingId", listingId, "status", target);
    }

    /** P1 为商品追加图库图片（与上传接口配合：先传后挂） */
    @ResaleTransactional
    public Map<String, Object> attachListingImage(Long listingId, String imageUrl) {
        ResaleListingEntity listing = requireListing(listingId);
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.length() > 255) {
            throw new BizException("图片地址不合法", ErrorCode.PARAM_INVALID);
        }
        com.suno.mall.entity.ProductImageEntity image = new com.suno.mall.entity.ProductImageEntity();
        image.setProduct(listing.getProduct());
        image.setImageUrl(imageUrl.trim());
        image.setSortOrder((int) productImageRepository.count() + 1);
        image.setImageType("PRODUCT");
        image.setCreatedAt(LocalDateTime.now());
        productImageRepository.save(image);
        auditLogService.logAction("RESALE_LISTING_IMAGE_ADD", "RESALE_LISTING", String.valueOf(listingId),
                "imageUrl=" + imageUrl.trim());
        return Map.of("listingId", listingId, "imageId", image.getId(), "imageUrl", image.getImageUrl());
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> pageOrders(String payStatus, String fulfillStatus, String keyword, Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        String likeKeyword = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase() + "%";
        Page<ResaleOrderEntity> result = resaleOrderRepository.adminPage(
                blankToNull(payStatus), blankToNull(fulfillStatus), likeKeyword,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return orderPage(result, safePage, safeSize);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> orderDetail(String orderNo) {
        ResaleOrderEntity order = resaleOrderRepository.findWithDetailsByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("订单不存在: " + orderNo, ErrorCode.ORDER_NOT_FOUND));
        Map<String, Object> detail = toOrderRow(order);
        detail.put("receiverName", order.getReceiverName());
        detail.put("receiverPhone", order.getReceiverPhone());
        detail.put("receiverAddress", order.getReceiverAddress());
        detail.put("discountAmount", order.getDiscountAmount());
        detail.put("payChannel", order.getPayChannel());
        detail.put("tradeNo", order.getTradeNo());
        detail.put("paidAt", order.getPaidAt());
        return detail;
    }

    private Map<String, Object> listingPage(Page<ResaleListingEntity> result, int safePage, int safeSize) {
        var content = result.getContent().stream().map(listing -> {
            Map<String, Object> row = new HashMap<>();
            row.put("listingId", listing.getId());
            row.put("brand", listing.getProduct().getBrand());
            row.put("model", listing.getProduct().getModel());
            row.put("grade", listing.getProduct().getRecycleGrade());
            row.put("salePrice", listing.getSalePrice());
            row.put("stock", listing.getStock());
            row.put("status", listing.getStatus());
            row.put("createdAt", listing.getCreatedAt());
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

    private Map<String, Object> orderPage(Page<ResaleOrderEntity> result, int page, int size) {
        var content = result.getContent().stream().map(AdminCatalogService::toOrderRow).toList();
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        payload.put("totalElements", result.getTotalElements());
        payload.put("totalPages", result.getTotalPages());
        payload.put("page", page);
        payload.put("size", size);
        return payload;
    }

    private static Map<String, Object> toOrderRow(ResaleOrderEntity order) {
        Map<String, Object> row = new HashMap<>();
        row.put("orderNo", order.getOrderNo());
        row.put("buyerUserId", order.getBuyerUser().getId());
        row.put("buyerUsername", order.getBuyerUser().getUsername());
        row.put("listingId", order.getListing().getId());
        row.put("productBrand", order.getListing().getProduct().getBrand());
        row.put("productModel", order.getListing().getProduct().getModel());
        row.put("amount", order.getAmount());
        row.put("payableAmount", order.getPayableAmount());
        row.put("payStatus", order.getPayStatus());
        row.put("fulfillStatus", order.getFulfillStatus());
        row.put("createdAt", order.getCreatedAt());
        return row;
    }

    private ResaleListingEntity requireListing(Long listingId) {
        return resaleListingRepository.findWithDetailsById(listingId)
                .orElseThrow(() -> new BizException("商品不存在: " + listingId, ErrorCode.LISTING_NOT_FOUND));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
