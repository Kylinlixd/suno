package com.suno.mall.controller;

import com.suno.mall.common.ApiResponse;
import com.suno.mall.entity.UserAddressEntity;
import com.suno.mall.service.AuthApplicationService;
import com.suno.mall.service.UserAddressService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * M2 收货地址管理（IDN-020 ~ IDN-024）。
 */
@Validated
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final UserAddressService userAddressService;
    private final AuthApplicationService authApplicationService;

    public AddressController(UserAddressService userAddressService, AuthApplicationService authApplicationService) {
        this.userAddressService = userAddressService;
        this.authApplicationService = authApplicationService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listAddresses(@AuthenticationPrincipal Jwt jwt) {
        Long userId = authApplicationService.requireUserId(jwt);
        List<Map<String, Object>> content = userAddressService.listByUser(userId).stream()
                .map(AddressController::toRow)
                .toList();
        return ApiResponse.ok(content);
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createAddress(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid AddressRequest request
    ) {
        UserAddressEntity address = userAddressService.create(
                authApplicationService.requireUserId(jwt),
                request.receiverName(), request.receiverPhone(),
                request.province(), request.city(), request.district(), request.detailAddress(),
                Boolean.TRUE.equals(request.isDefault()));
        return ApiResponse.ok(toRow(address));
    }

    @PutMapping("/{addressId}")
    public ApiResponse<Map<String, Object>> updateAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long addressId,
            @RequestBody @Valid AddressRequest request
    ) {
        UserAddressEntity address = userAddressService.update(
                authApplicationService.requireUserId(jwt), addressId,
                request.receiverName(), request.receiverPhone(),
                request.province(), request.city(), request.district(), request.detailAddress());
        return ApiResponse.ok(toRow(address));
    }

    @DeleteMapping("/{addressId}")
    public ApiResponse<Map<String, Object>> deleteAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long addressId
    ) {
        userAddressService.delete(authApplicationService.requireUserId(jwt), addressId);
        return ApiResponse.ok(Map.of("addressId", addressId, "deleted", true));
    }

    @PostMapping("/{addressId}/default")
    public ApiResponse<Map<String, Object>> setDefaultAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long addressId
    ) {
        UserAddressEntity address = userAddressService.setDefault(authApplicationService.requireUserId(jwt), addressId);
        return ApiResponse.ok(toRow(address));
    }

    static Map<String, Object> toRow(UserAddressEntity address) {
        return Map.of(
                "addressId", address.getId(),
                "receiverName", address.getReceiverName(),
                "receiverPhone", address.getReceiverPhone(),
                "province", address.getProvince(),
                "city", address.getCity(),
                "district", address.getDistrict(),
                "detailAddress", address.getDetailAddress(),
                "isDefault", Boolean.TRUE.equals(address.getIsDefault())
        );
    }

    public record AddressRequest(
            @NotBlank String receiverName,
            @NotBlank String receiverPhone,
            @NotBlank String province,
            @NotBlank String city,
            @NotBlank String district,
            @NotBlank String detailAddress,
            Boolean isDefault
    ) {
    }
}
