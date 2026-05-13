package com.suno.mall.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suno.mall.service.RecycleApplicationService;
import com.suno.mall.common.ApiResponse;
import com.suno.mall.common.CacheContract;
import com.suno.mall.service.PaymentSignatureService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/mall")
public class ResaleMallController {

    private final RecycleApplicationService recycleApplicationService;
    private final PaymentSignatureService paymentSignatureService;
    private final ObjectMapper objectMapper;

    public ResaleMallController(
            RecycleApplicationService recycleApplicationService,
            PaymentSignatureService paymentSignatureService,
            ObjectMapper objectMapper
    ) {
        this.recycleApplicationService = recycleApplicationService;
        this.paymentSignatureService = paymentSignatureService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/listings")
    public ApiResponse<List<Map<String, Object>>> listListings(
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) Integer minStock
    ) {
        return ApiResponse.ok(recycleApplicationService.listResaleListings(grade, sortBy, sortOrder, minStock));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listBuyerOrders(
            @RequestParam Long buyerUserId,
            @RequestParam(required = false) String payStatus,
            @RequestParam(required = false) String fulfillStatus,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) @Min(1) @Max(100) Integer limit,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        Map<String, Object> payload = recycleApplicationService.listBuyerResaleOrders(
                buyerUserId,
                payStatus,
                fulfillStatus,
                sortBy,
                sortOrder,
                limit,
                page,
                size
        );
        return buildEtagOnlyResponse(payload, ifNoneMatch);
    }

    @GetMapping("/orders/status-dictionary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrderStatusDictionary(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        Map<String, Object> payload = recycleApplicationService.getResaleOrderStatusDictionary();
        return buildCacheableResponse(payload, ifNoneMatch);
    }

    @GetMapping("/orders/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summarizeBuyerOrders(
            @RequestParam Long buyerUserId,
            @RequestParam(required = false) @Min(1) @Max(365) Integer lookbackDays,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        Map<String, Object> payload = recycleApplicationService.summarizeBuyerResaleOrders(buyerUserId, lookbackDays);
        return buildEtagOnlyResponse(payload, ifNoneMatch);
    }

    @PostMapping("/orders")
    public ApiResponse<Map<String, Object>> createOrder(@RequestBody CreateResaleOrderRequest request) {
        return ApiResponse.ok(recycleApplicationService.createResaleOrder(
                request.buyerUserId(),
                request.listingId()
        ));
    }

    @PostMapping("/orders/pay")
    public ApiResponse<Map<String, Object>> payOrder(@RequestBody PayResaleOrderRequest request) {
        paymentSignatureService.verifyOrThrow(
                request.orderNo(),
                request.idempotencyKey(),
                request.timestamp(),
                request.nonce(),
                request.signature()
        );
        return ApiResponse.ok(recycleApplicationService.markResaleOrderPaidWithIdempotency(
                request.orderNo(),
                request.idempotencyKey()
        ));
    }

    @PostMapping("/orders/cancel")
    public ApiResponse<Map<String, Object>> cancelOrder(@RequestBody CancelResaleOrderRequest request) {
        return ApiResponse.ok(recycleApplicationService.cancelUnpaidResaleOrder(request.orderNo()));
    }

    @PostMapping("/orders/confirm-receipt")
    public ApiResponse<Map<String, Object>> confirmReceipt(@RequestBody ConfirmResaleOrderReceiptRequest request) {
        return ApiResponse.ok(recycleApplicationService.confirmResaleOrderReceipt(
                request.orderNo(),
                request.buyerUserId()
        ));
    }

    @GetMapping("/orders/{orderNo}/track")
    public ApiResponse<Map<String, Object>> queryOrderTrack(
            @PathVariable String orderNo,
            @RequestParam Long buyerUserId
    ) {
        return ApiResponse.ok(recycleApplicationService.queryResaleOrderTrack(orderNo, buyerUserId));
    }

    @PostMapping("/favorites/add")
    public ApiResponse<Map<String, Object>> addFavorite(@RequestBody FavoriteRequest request) {
        return ApiResponse.ok(recycleApplicationService.addFavoriteListing(request.userId(), request.listingId()));
    }

    @PostMapping("/favorites/remove")
    public ApiResponse<Map<String, Object>> removeFavorite(@RequestBody FavoriteRequest request) {
        return ApiResponse.ok(recycleApplicationService.removeFavoriteListing(request.userId(), request.listingId()));
    }

    @GetMapping("/favorites")
    public ApiResponse<List<Map<String, Object>>> listFavorites(@RequestParam Long userId) {
        return ApiResponse.ok(recycleApplicationService.listFavoriteListings(userId));
    }

    @PostMapping("/reviews/create")
    public ApiResponse<Map<String, Object>> createReview(@RequestBody CreateReviewRequest request) {
        return ApiResponse.ok(recycleApplicationService.createResaleReview(
                request.orderNo(),
                request.buyerUserId(),
                request.rating(),
                request.content(),
                request.imageUrls()
        ));
    }

    @PostMapping("/reviews/append")
    public ApiResponse<Map<String, Object>> appendReview(@RequestBody AppendReviewRequest request) {
        return ApiResponse.ok(recycleApplicationService.appendResaleReview(
                request.orderNo(),
                request.buyerUserId(),
                request.appendContent()
        ));
    }

    @PostMapping("/reviews/reply")
    public ApiResponse<Map<String, Object>> replyReview(@RequestBody ReplyReviewRequest request) {
        return ApiResponse.ok(recycleApplicationService.replyResaleReview(
                request.orderNo(),
                request.merchantReply(),
                request.operator()
        ));
    }

    @GetMapping("/reviews")
    public ApiResponse<Map<String, Object>> listReviews(
            @RequestParam Long listingId,
            @RequestParam(required = false, defaultValue = "SMART") String sortStrategy,
            @RequestParam(required = false) Boolean includeHidden
    ) {
        if (includeHidden == null) {
            return ApiResponse.ok(recycleApplicationService.listResaleReviews(listingId, sortStrategy));
        }
        return ApiResponse.ok(recycleApplicationService.listResaleReviews(listingId, sortStrategy, includeHidden));
    }

    @PostMapping("/reviews/vote-useful")
    public ApiResponse<Map<String, Object>> voteReviewUseful(@RequestBody VoteReviewUsefulRequest request) {
        return ApiResponse.ok(recycleApplicationService.voteResaleReviewUseful(
                request.orderNo(),
                request.voterUserId()
        ));
    }

    @PostMapping("/reviews/report")
    public ApiResponse<Map<String, Object>> reportReview(@RequestBody ReportReviewRequest request) {
        return ApiResponse.ok(recycleApplicationService.reportResaleReview(
                request.orderNo(),
                request.reporterUserId(),
                request.reason()
        ));
    }

    public record CreateResaleOrderRequest(
            @NotNull Long buyerUserId,
            @NotNull Long listingId
    ) {
    }

    public record PayResaleOrderRequest(
            @NotBlank String orderNo,
            @NotBlank String idempotencyKey,
            long timestamp,
            @NotBlank String nonce,
            @NotBlank String signature
    ) {
    }

    public record CancelResaleOrderRequest(
            @NotBlank String orderNo
    ) {
    }

    public record ConfirmResaleOrderReceiptRequest(
            @NotBlank String orderNo,
            @NotNull Long buyerUserId
    ) {
    }

    public record FavoriteRequest(
            @NotNull Long userId,
            @NotNull Long listingId
    ) {
    }

    public record CreateReviewRequest(
            @NotBlank String orderNo,
            @NotNull Long buyerUserId,
            @Min(1) @Max(5) int rating,
            @NotBlank String content,
            List<String> imageUrls
    ) {
    }

    public record AppendReviewRequest(
            @NotBlank String orderNo,
            @NotNull Long buyerUserId,
            @NotBlank String appendContent
    ) {
    }

    public record ReplyReviewRequest(
            @NotBlank String orderNo,
            @NotBlank String merchantReply,
            String operator
    ) {
    }

    public record VoteReviewUsefulRequest(
            @NotBlank String orderNo,
            @NotNull Long voterUserId
    ) {
    }

    public record ReportReviewRequest(
            @NotBlank String orderNo,
            @NotNull Long reporterUserId,
            @NotBlank String reason
    ) {
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> buildCacheableResponse(
            Map<String, Object> payload,
            String ifNoneMatch
    ) {
        CacheDigest cacheDigest = buildCacheDigest(payload);
        String digest = cacheDigest.digest();
        String etag = cacheDigest.etag();
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, etag)
                    .header(CacheContract.HEADER_CACHE_DIGEST, digest)
                    .build();
        }
        Map<String, Object> enrichedPayload = new java.util.LinkedHashMap<>(payload);
        enrichedPayload.put(CacheContract.FIELD_CACHE_DIGEST, digest);
        String lastModified = ((LocalDateTime) payload.get("generatedAt"))
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, etag)
                .header(CacheContract.HEADER_CACHE_DIGEST, digest)
                .header(HttpHeaders.LAST_MODIFIED, lastModified)
                .body(ApiResponse.ok(enrichedPayload));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> buildEtagOnlyResponse(
            Map<String, Object> payload,
            String ifNoneMatch
    ) {
        CacheDigest cacheDigest = buildCacheDigest(payload);
        String digest = cacheDigest.digest();
        String etag = cacheDigest.etag();
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, etag)
                    .header(CacheContract.HEADER_CACHE_DIGEST, digest)
                    .build();
        }
        Map<String, Object> enrichedPayload = new java.util.LinkedHashMap<>(payload);
        enrichedPayload.put(CacheContract.FIELD_CACHE_DIGEST, digest);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, etag)
                .header(CacheContract.HEADER_CACHE_DIGEST, digest)
                .body(ApiResponse.ok(enrichedPayload));
    }

    private CacheDigest buildCacheDigest(Map<String, Object> payload) {
        String bodyJson = toJson(payload);
        String digest = DigestUtils.md5DigestAsHex(bodyJson.getBytes(StandardCharsets.UTF_8));
        return new CacheDigest(digest, "\"" + digest + "\"");
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("状态字典序列化失败");
        }
    }

    private record CacheDigest(String digest, String etag) {
    }
}
