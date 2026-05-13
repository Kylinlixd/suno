
package com.suno.mall.service;

import com.recycle.mall.entity.RecycleOrderEntity;
import com.recycle.mall.entity.ResaleFavoriteEntity;
import com.recycle.mall.entity.ResaleListingEntity;
import com.recycle.mall.entity.UserAccountEntity;
import com.recycle.mall.dao.RecycleOrderRepository;
import com.recycle.mall.dao.ResaleFavoriteRepository;
import com.recycle.mall.dao.ResaleListingRepository;
import com.recycle.mall.dao.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 二销商品服务
 */
@Service
public class ResaleListingService {

    private static final String STATUS_LISTED = "LISTED";
    private static final String LISTING_STATUS_ON_SHELF = "ON_SHELF";
    private static final String LISTING_STATUS_SOLD_OUT = "SOLD_OUT";

    private final RecycleOrderRepository recycleOrderRepository;
    private final ResaleListingRepository resaleListingRepository;
    private final ResaleFavoriteRepository resaleFavoriteRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuditLogService auditLogService;

    public ResaleListingService(
            RecycleOrderRepository recycleOrderRepository,
            ResaleListingRepository resaleListingRepository,
            ResaleFavoriteRepository resaleFavoriteRepository,
            UserAccountRepository userAccountRepository,
            AuditLogService auditLogService
    ) {
        this.recycleOrderRepository = recycleOrderRepository;
        this.resaleListingRepository = resaleListingRepository;
        this.resaleFavoriteRepository = resaleFavoriteRepository;
        this.userAccountRepository = userAccountRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Map<String, Object> publishResaleListing(String recycleOrderNo, java.math.BigDecimal salePrice, int stock) {
        RecycleOrderEntity recycleOrder = recycleOrderRepository.findByOrderNo(recycleOrderNo)
                .orElseThrow(() -> new IllegalArgumentException("回收单不存在: " + recycleOrderNo));
        if (!STATUS_LISTED.equals(recycleOrder.getStatus())) {
            throw new IllegalArgumentException("仅 LISTED 状态回收单可发布二销商品");
        }
        ResaleListingEntity listing = new ResaleListingEntity();
        listing.setRecycleOrder(recycleOrder);
        listing.setProduct(recycleOrder.getProduct());
        listing.setSalePrice(salePrice);
        listing.setStock(stock);
        listing.setStatus(LISTING_STATUS_ON_SHELF);
        listing.setCreatedAt(LocalDateTime.now());
        resaleListingRepository.save(listing);
        auditLogService.logAction("RESALE_LISTING_PUBLISH", "RESALE_LISTING", String.valueOf(listing.getId()), "recycleOrderNo=" + recycleOrderNo);
        return Map.of(
                "listingId", listing.getId(),
                "recycleOrderNo", recycleOrderNo,
                "salePrice", listing.getSalePrice(),
                "stock", listing.getStock(),
                "status", listing.getStatus()
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listResaleListings() {
        return listResaleListings(null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listResaleListings(
            String grade, String sortBy, String sortOrder, Integer minStock
    ) {
        List<ResaleListingEntity> listings = resaleListingRepository.findByStatus(LISTING_STATUS_ON_SHELF).stream()
                .filter(item -> grade == null || grade.isBlank() || grade.equalsIgnoreCase(item.getProduct().getRecycleGrade()))
                .filter(item -> minStock == null || item.getStock() >= minStock)
                .toList();
        Comparator<ResaleListingEntity> comparator = buildListingComparator(sortBy);
        String safeSortOrder = sortOrder == null ? "asc" : sortOrder.toLowerCase(Locale.ROOT);
        if ("desc".equals(safeSortOrder)) {
            comparator = comparator.reversed();
        }
        return listings.stream().sorted(comparator).map(item -> Map.<String, Object>ofEntries(
                Map.entry("listingId", item.getId()),
                Map.entry("brand", item.getProduct().getBrand()),
                Map.entry("model", item.getProduct().getModel()),
                Map.entry("grade", item.getProduct().getRecycleGrade()),
                Map.entry("salePrice", item.getSalePrice()),
                Map.entry("stock", item.getStock()),
                Map.entry("favoriteCount", resaleFavoriteRepository.countByListing_Id(item.getId()))
        )).toList();
    }

    @Transactional
    public Map<String, Object> addFavoriteListing(Long userId, Long listingId) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        ResaleListingEntity listing = resaleListingRepository.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在: " + listingId));
        if (!LISTING_STATUS_ON_SHELF.equals(listing.getStatus())) {
            throw new IllegalArgumentException("仅可收藏在售商品");
        }
        ResaleFavoriteEntity existing = resaleFavoriteRepository.findByUser_IdAndListing_Id(userId, listingId).orElse(null);
        if (existing != null) {
            return Map.of("userId", userId, "listingId", listingId, "favorited", true, "idempotentReplay", true);
        }
        ResaleFavoriteEntity favorite = new ResaleFavoriteEntity();
        favorite.setUser(user);
        favorite.setListing(listing);
        favorite.setCreatedAt(LocalDateTime.now());
        resaleFavoriteRepository.save(favorite);
        auditLogService.logAction("RESALE_FAVORITE_ADD", "RESALE_LISTING", String.valueOf(listingId), "userId=" + userId);
        return Map.of("userId", userId, "listingId", listingId, "favorited", true, "idempotentReplay", false);
    }

    @Transactional
    public Map<String, Object> removeFavoriteListing(Long userId, Long listingId) {
        ResaleFavoriteEntity favorite = resaleFavoriteRepository.findByUser_IdAndListing_Id(userId, listingId)
                .orElseThrow(() -> new IllegalArgumentException("收藏记录不存在"));
        resaleFavoriteRepository.delete(favorite);
        auditLogService.logAction("RESALE_FAVORITE_REMOVE", "RESALE_LISTING", String.valueOf(listingId), "userId=" + userId);
        return Map.of("userId", userId, "listingId", listingId, "favorited", false);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listFavoriteListings(Long userId) {
        userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        return resaleFavoriteRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream().map(item -> Map.<String, Object>ofEntries(
                Map.entry("listingId", item.getListing().getId()),
                Map.entry("brand", item.getListing().getProduct().getBrand()),
                Map.entry("model", item.getListing().getProduct().getModel()),
                Map.entry("grade", item.getListing().getProduct().getRecycleGrade()),
                Map.entry("salePrice", item.getListing().getSalePrice()),
                Map.entry("stock", item.getListing().getStock()),
                Map.entry("favoriteAt", item.getCreatedAt())
        )).toList();
    }

    void restoreListingStock(ResaleListingEntity listing) {
        listing.setStock(listing.getStock() + 1);
        listing.setStatus(LISTING_STATUS_ON_SHELF);
        resaleListingRepository.save(listing);
    }

    private Comparator<ResaleListingEntity> buildListingComparator(String sortBy) {
        String safeSortBy = sortBy == null ? "createdAt" : sortBy.toLowerCase(Locale.ROOT);
        return switch (safeSortBy) {
            case "price" -> Comparator.comparing(ResaleListingEntity::getSalePrice);
            case "stock" -> Comparator.comparing(ResaleListingEntity::getStock);
            default -> Comparator.comparing(ResaleListingEntity::getCreatedAt);
        };
    }
}
