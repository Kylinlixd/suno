package com.suno.mall.controller;

import com.suno.mall.common.ApiResponse;
import com.suno.mall.service.AuthApplicationService;
import com.suno.mall.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * M2 购物车（MKT-040 ~ MKT-042）。
 */
@Validated
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;
    private final AuthApplicationService authApplicationService;

    public CartController(CartService cartService, AuthApplicationService authApplicationService) {
        this.cartService = cartService;
        this.authApplicationService = authApplicationService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> listCart(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(cartService.listCart(authApplicationService.requireUserId(jwt)));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> addToCart(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid AddCartItemRequest request
    ) {
        return ApiResponse.ok(cartService.addToCart(authApplicationService.requireUserId(jwt), request.listingId()));
    }

    @DeleteMapping("/{cartItemId}")
    public ApiResponse<Map<String, Object>> removeFromCart(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long cartItemId
    ) {
        return ApiResponse.ok(cartService.removeFromCart(authApplicationService.requireUserId(jwt), cartItemId));
    }

    public record AddCartItemRequest(@NotNull Long listingId) {
    }
}
