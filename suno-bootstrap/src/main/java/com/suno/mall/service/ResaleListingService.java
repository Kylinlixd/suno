
package com.suno.mall.service;

import com.suno.mall.common.BizException;
import com.suno.mall.common.ErrorCode;
import com.suno.mall.entity.CategoryEntity;
import com.suno.mall.entity.RecycleOrderEntity;
import com.suno.mall.entity.ResaleFavoriteEntity;
import com.suno.mall.entity.ResaleListingEntity;
import com.suno.mall.entity.UserAccountEntity;
import com.suno.mall.dao.CategoryRepository;
import com.suno.mall.dao.RecycleOrderRepository;
import com.suno.mall.dao.ResaleFavoriteRepository;
import com.suno.mall.dao.ResaleListingRepository;
import com.suno.mall.dao.UserAccountRepository;
import org.springframework.stereotype.Service;
import com.suno.mall.config.ResaleTransactional;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
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
    private final CategoryRepository categoryRepository;
    private final com.suno.mall.dao.ProductImageRepository productImageRepository;
    private final ResaleReviewService resaleReviewService;
    private final AuditLogService auditLogService;

    public ResaleListingService(
            RecycleOrderRepository recycleOrderRepository,
            ResaleListingRepository resaleListingRepository,
            ResaleFavoriteRepository resaleFavoriteRepository,
            UserAccountRepository userAccountRepository,
            CategoryRepository categoryRepository,
            com.suno.mall.dao.ProductImageRepository productImageRepository,
            ResaleReviewService resaleReviewService,
            AuditLogService auditLogService
    ) {
        this.recycleOrderRepository = recycleOrderRepository;
        this.resaleListingRepository = resaleListingRepository;
        this.resaleFavoriteRepository = resaleFavoriteRepository;
        this.userAccountRepository = userAccountRepository;
        this.categoryRepository = categoryRepository;
        this.productImageRepository = productImageRepository;
        this.resaleReviewService = resaleReviewService;
        this.auditLogService = auditLogService;
    }

    @ResaleTransactional
    public Map<String, Object> publishResaleListing(String recycleOrderNo, java.math.BigDecimal salePrice, int stock) {
        RecycleOrderEntity recycleOrder = recycleOrderRepository.findWithDetailsByOrderNo(recycleOrderNo)
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
        listing.setUpdatedAt(LocalDateTime.now());
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

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public List<Map<String, Object>> listResaleListings() {
        return listResaleListings(null, null, null, null);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public List<Map<String, Object>> listResaleListings(
            String grade, String sortBy, String sortOrder, Integer minStock
    ) {
        return filterListings(null, null, grade, minStock).stream()
                .sorted(buildListingComparator(sortBy, sortOrder))
                .map(this::toListingSummary)
                .toList();
    }

    /**
     * M1 商品搜索：关键词（品牌/型号/SN 模糊匹配）+ 分类 + 成色过滤，内存分页。
     * 返回零基分页信封：content/totalElements/totalPages/page/size。
     */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> searchResaleListings(
            String keyword, Long categoryId, String grade, String sortBy, String sortOrder,
            Integer minStock, Integer page, Integer size
    ) {
        if (categoryId != null) {
            categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new BizException("分类不存在: " + categoryId, ErrorCode.CATEGORY_NOT_FOUND));
        }
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        List<ResaleListingEntity> filtered = filterListings(normalizeKeyword(keyword), categoryId, grade, minStock);
        List<ResaleListingEntity> sorted = filtered.stream()
                .sorted(buildListingComparator(sortBy, sortOrder))
                .toList();
        int fromIndex = Math.min(safePage * safeSize, sorted.size());
        int toIndex = Math.min(fromIndex + safeSize, sorted.size());
        List<Map<String, Object>> content = sorted.subList(fromIndex, toIndex).stream()
                .map(this::toListingSummary)
                .toList();
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", sorted.size());
        result.put("totalPages", (sorted.size() + safeSize - 1) / safeSize);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    /**
     * M1 单商品详情：在售与售罄均可见，包含商品图、分类、成色与评价摘要。
     */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> getListingDetail(Long listingId) {
        ResaleListingEntity listing = resaleListingRepository.findWithDetailsById(listingId)
                .orElseThrow(() -> new BizException("商品不存在: " + listingId, ErrorCode.LISTING_NOT_FOUND));
        Map<String, Object> detail = new HashMap<>(toListingSummary(listing));
        detail.put("status", listing.getStatus());
        detail.put("createdAt", listing.getCreatedAt());
        java.util.List<String> gallery = new java.util.ArrayList<>();
        gallery.add(listing.getProduct().getImageUrl());
        productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(listing.getProduct().getId())
                .forEach(image -> gallery.add(image.getImageUrl()));
        detail.put("images", gallery.stream().distinct().toList());
        Map<String, Object> reviewSummary = resaleReviewService.listResaleReviews(listingId);
        detail.put("reviewCount", reviewSummary.getOrDefault("reviewCount", 0));
        detail.put("avgRating", reviewSummary.getOrDefault("avgRating", 0));
        return detail;
    }

    /**
     * M1 分类目录：仅返回启用分类，按 sortOrder 排序。
     */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public List<Map<String, Object>> listCategories() {
        return categoryRepository.findByStatusOrderBySortOrderAscIdAsc("ENABLED").stream()
                .map(ResaleListingService::toCategorySummary)
                .toList();
    }

    private static Map<String, Object> toCategorySummary(CategoryEntity category) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("categoryId", category.getId());
        summary.put("name", category.getName());
        summary.put("parentId", category.getParentId());
        summary.put("sortOrder", category.getSortOrder());
        return summary;
    }

    private List<ResaleListingEntity> filterListings(String keyword, Long categoryId, String grade, Integer minStock) {
        return resaleListingRepository.findByStatusWithDetails(LISTING_STATUS_ON_SHELF).stream()
                .filter(item -> grade == null || grade.isBlank() || grade.equalsIgnoreCase(item.getProduct().getRecycleGrade()))
                .filter(item -> minStock == null || item.getStock() >= minStock)
                .filter(item -> categoryId == null || categoryId.equals(item.getProduct().getCategoryId()))
                .filter(item -> keyword == null
                        || item.getProduct().getBrand().toLowerCase(Locale.ROOT).contains(keyword)
                        || item.getProduct().getModel().toLowerCase(Locale.ROOT).contains(keyword)
                        || item.getProduct().getSnCode().toLowerCase(Locale.ROOT).contains(keyword))
                .toList();
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> toListingSummary(ResaleListingEntity item) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("listingId", item.getId());
        summary.put("brand", item.getProduct().getBrand());
        summary.put("model", item.getProduct().getModel());
        summary.put("grade", item.getProduct().getRecycleGrade());
        summary.put("salePrice", item.getSalePrice());
        summary.put("stock", item.getStock());
        summary.put("image", item.getProduct().getImageUrl());
        summary.put("favoriteCount", resaleFavoriteRepository.countByListing_Id(item.getId()));
        Long categoryId = item.getProduct().getCategoryId();
        if (categoryId != null) {
            summary.put("categoryId", categoryId);
            categoryRepository.findById(categoryId)
                    .map(CategoryEntity::getName)
                    .ifPresent(name -> summary.put("categoryName", name));
        }
        return summary;
    }

    @ResaleTransactional
    public Map<String, Object> addFavoriteListing(Long userId, Long listingId) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        ResaleListingEntity listing = resaleListingRepository.findWithDetailsById(listingId)
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

    @ResaleTransactional
    public Map<String, Object> removeFavoriteListing(Long userId, Long listingId) {
        ResaleFavoriteEntity favorite = resaleFavoriteRepository.findByUser_IdAndListing_Id(userId, listingId)
                .orElseThrow(() -> new IllegalArgumentException("收藏记录不存在"));
        resaleFavoriteRepository.delete(favorite);
        auditLogService.logAction("RESALE_FAVORITE_REMOVE", "RESALE_LISTING", String.valueOf(listingId), "userId=" + userId);
        return Map.of("userId", userId, "listingId", listingId, "favorited", false);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
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
        listing.setUpdatedAt(LocalDateTime.now());
        resaleListingRepository.save(listing);
    }

    /**
     * 减少商品库存
     * 当库存减少到0时，自动将状态设置为售罄
     */
    @ResaleTransactional
    public Map<String, Object> reduceListingStock(Long listingId, int quantity) {
        ResaleListingEntity listing = resaleListingRepository.findWithDetailsById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在: " + listingId));
        
        if (!LISTING_STATUS_ON_SHELF.equals(listing.getStatus())) {
            throw new IllegalArgumentException("仅可减少在售商品的库存");
        }
        
        if (quantity <= 0) {
            throw new IllegalArgumentException("减少数量必须大于0");
        }
        
        if (listing.getStock() < quantity) {
            throw new IllegalArgumentException("库存不足，当前库存: " + listing.getStock() + ", 请求减少: " + quantity);
        }
        
        // 减少库存
        listing.setStock(listing.getStock() - quantity);
        
        // 如果库存减少到0，将状态设置为售罄
        if (listing.getStock() == 0) {
            listing.setStatus(LISTING_STATUS_SOLD_OUT);
            auditLogService.logAction("RESALE_LISTING_SOLD_OUT", "RESALE_LISTING", String.valueOf(listingId), "stock=" + listing.getStock());
        }
        
        listing.setUpdatedAt(LocalDateTime.now());
        resaleListingRepository.save(listing);
        return Map.of(
                "listingId", listing.getId(),
                "stock", listing.getStock(),
                "status", listing.getStatus()
        );
    }

    /**
     * 查询已售罄的商品列表
     */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public List<Map<String, Object>> listSoldOutListings() {
        return listSoldOutListings(null, null);
    }

    /**
     * 查询已售罄的商品列表（带过滤条件）
 */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public List<Map<String, Object>> listSoldOutListings(String grade, String sortBy) {
        List<ResaleListingEntity> listings = resaleListingRepository.findByStatusWithDetails(LISTING_STATUS_SOLD_OUT).stream()
                .filter(item -> grade == null || grade.isBlank() || grade.equalsIgnoreCase(item.getProduct().getRecycleGrade()))
                .toList();
        
        Comparator<ResaleListingEntity> comparator = buildListingComparator(sortBy);
        return listings.stream().sorted(comparator).map(item -> Map.<String, Object>ofEntries(
                Map.entry("listingId", item.getId()),
                Map.entry("brand", item.getProduct().getBrand()),
                Map.entry("model", item.getProduct().getModel()),
                Map.entry("grade", item.getProduct().getRecycleGrade()),
                Map.entry("salePrice", item.getSalePrice()),
                Map.entry("stock", item.getStock()),
                Map.entry("status", item.getStatus()),
                Map.entry("soldAt", item.getUpdatedAt())
        )).toList();
    }

    private Comparator<ResaleListingEntity> buildListingComparator(String sortBy, String sortOrder) {
        Comparator<ResaleListingEntity> comparator = buildListingComparator(sortBy);
        String safeSortOrder = sortOrder == null ? "asc" : sortOrder.toLowerCase(Locale.ROOT);
        return "desc".equals(safeSortOrder) ? comparator.reversed() : comparator;
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
