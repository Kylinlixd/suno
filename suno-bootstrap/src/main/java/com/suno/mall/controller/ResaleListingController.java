
package com.suno.mall.controller;

import com.suno.mall.service.ResaleListingService;
import com.suno.mall.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 二销商品控制器
 */
@Validated
@RestController
@RequestMapping("/api/resale/listings")
public class ResaleListingController {

    private final ResaleListingService resaleListingService;

    public ResaleListingController(ResaleListingService resaleListingService) {
        this.resaleListingService = resaleListingService;
    }

    /**
     * 发布二销商品
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> publishListing(
            @RequestParam String recycleOrderNo,
            @RequestParam java.math.BigDecimal salePrice,
            @RequestParam int stock
    ) {
        return ApiResponse.ok(resaleListingService.publishResaleListing(recycleOrderNo, salePrice, stock));
    }

    /**
     * 查询二销商品列表
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listListings(
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) Integer minStock
    ) {
        return ApiResponse.ok(resaleListingService.listResaleListings(grade, sortBy, sortOrder, minStock));
    }

    /**
     * 查询已售罄的商品列表
     */
    @GetMapping("/sold-out")
    public ApiResponse<List<Map<String, Object>>> listSoldOutListings(
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String sortBy
    ) {
        return ApiResponse.ok(resaleListingService.listSoldOutListings(grade, sortBy));
    }

    /**
     * 减少商品库存
     */
    @PostMapping("/{listingId}/reduce-stock")
    public ApiResponse<Map<String, Object>> reduceStock(
            @PathVariable Long listingId,
            @RequestParam int quantity
    ) {
        return ApiResponse.ok(resaleListingService.reduceListingStock(listingId, quantity));
    }

    /**
     * 添加收藏
     */
    @PostMapping("/{listingId}/favorite")
    public ApiResponse<Map<String, Object>> addFavorite(
            @RequestParam Long userId,
            @PathVariable Long listingId
    ) {
        return ApiResponse.ok(resaleListingService.addFavoriteListing(userId, listingId));
    }

    /**
     * 取消收藏
     */
    @DeleteMapping("/{listingId}/favorite")
    public ApiResponse<Map<String, Object>> removeFavorite(
            @RequestParam Long userId,
            @PathVariable Long listingId
    ) {
        return ApiResponse.ok(resaleListingService.removeFavoriteListing(userId, listingId));
    }

    /**
     * 查询用户收藏列表
     */
    @GetMapping("/favorites")
    public ApiResponse<List<Map<String, Object>>> listFavorites(
            @RequestParam Long userId
    ) {
        return ApiResponse.ok(resaleListingService.listFavoriteListings(userId));
    }
}
