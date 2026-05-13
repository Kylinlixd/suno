package com.suno.mall.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suno.mall.service.RecycleApplicationService;
import com.suno.mall.service.support.AuditContext;
import com.suno.mall.common.ApiResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.DigestUtils;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/admin/recycle")
public class AdminRecycleController {

    private final RecycleApplicationService recycleApplicationService;
    private final ObjectMapper objectMapper;

    public AdminRecycleController(RecycleApplicationService recycleApplicationService, ObjectMapper objectMapper) {
        this.recycleApplicationService = recycleApplicationService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/orders")
    public ApiResponse<List<Map<String, Object>>> listOrders() {
        return ApiResponse.ok(recycleApplicationService.listRecycleOrders());
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<Map<String, Object>>> listAuditLogs(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String targetId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(recycleApplicationService.listAuditLogs(actionType, targetId, limit));
    }

    @GetMapping("/audit-logs/page")
    public ApiResponse<Map<String, Object>> pageAuditLogs(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(recycleApplicationService.pageAuditLogs(actionType, targetId, page, size));
    }

    @GetMapping("/audit-logs/export")
    public ResponseEntity<String> exportAuditLogs(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String targetId,
            @RequestParam(defaultValue = "1000") int limit
    ) {
        String csv = recycleApplicationService.exportAuditLogsCsv(actionType, targetId, limit);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-logs.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    @PatchMapping("/orders/review")
    public ApiResponse<Map<String, Object>> reviewOrder(@RequestBody ReviewOrderRequest request) {
        return ApiResponse.ok(recycleApplicationService.transitionOrder(
                request.orderNo(),
                request.action(),
                request.reviewedGrade()
        ));
    }

    @PostMapping("/listings/publish")
    public ApiResponse<Map<String, Object>> publishListing(@RequestBody PublishListingRequest request) {
        return ApiResponse.ok(recycleApplicationService.publishResaleListing(
                request.recycleOrderNo(),
                request.salePrice(),
                request.stock()
        ));
    }

    @PostMapping("/resale-orders/deliver")
    public ApiResponse<Map<String, Object>> deliverResaleOrder(@RequestBody DeliverResaleOrderRequest request) {
        return ApiResponse.ok(recycleApplicationService.deliverResaleOrder(
                request.orderNo(),
                toAuditContext(request.requestId(), request.changeSummary())
        ));
    }

    @PostMapping("/resale-orders/refund")
    public ApiResponse<Map<String, Object>> refundResaleOrder(@RequestBody RefundResaleOrderRequest request) {
        return ApiResponse.ok(recycleApplicationService.refundPaidResaleOrder(
                request.orderNo(),
                toAuditContext(request.requestId(), request.changeSummary())
        ));
    }

    @PostMapping("/resale-orders/auto-confirm-receipt")
    public ApiResponse<Map<String, Object>> autoConfirmResaleOrderReceipt(
            @RequestBody(required = false) AutoConfirmReceiptRequest request
    ) {
        int confirmAfterMinutes = request == null || request.confirmAfterMinutes() == null
                ? 4320
                : request.confirmAfterMinutes();
        int batchSize = request == null || request.batchSize() == null
                ? 200
                : request.batchSize();
        return ApiResponse.ok(Map.of(
                "confirmAfterMinutes", confirmAfterMinutes,
                "batchSize", batchSize,
                "confirmedCount", recycleApplicationService.autoConfirmDeliveredOrders(
                        confirmAfterMinutes,
                        batchSize,
                        toAuditContext(
                                request == null ? null : request.requestId(),
                                request == null ? null : request.changeSummary()
                        )
                )
        ));
    }

    @GetMapping("/review-reports")
    public ApiResponse<List<Map<String, Object>>> listReviewReports(
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(recycleApplicationService.adminListReviewReports(status));
    }

    @GetMapping("/review-reports/{reportId}")
    public ApiResponse<Map<String, Object>> getReviewReport(@PathVariable Long reportId) {
        return ApiResponse.ok(recycleApplicationService.adminGetReviewReport(reportId));
    }

    @PostMapping("/review-reports/process")
    public ApiResponse<Map<String, Object>> processReviewReport(@RequestBody ProcessReviewReportRequest request) {
        return ApiResponse.ok(recycleApplicationService.adminProcessReviewReport(
                request.reportId(),
                request.action(),
                request.processNote(),
                request.operator(),
                toAuditContext(request.requestId(), request.changeSummary())
        ));
    }

    @PostMapping("/review-reports/process-batch")
    public ApiResponse<Map<String, Object>> processReviewReportsBatch(@RequestBody ProcessReviewReportsBatchRequest request) {
        return ApiResponse.ok(recycleApplicationService.adminBatchProcessReviewReports(
                request.reportIds(),
                request.action(),
                request.processNote(),
                request.operator(),
                toAuditContext(request.requestId(), request.changeSummary())
        ));
    }

    @GetMapping("/review-risk/summary")
    public ApiResponse<Map<String, Object>> reviewRiskSummary(
            @RequestParam(defaultValue = "60") int lookbackMinutes
    ) {
        return ApiResponse.ok(recycleApplicationService.adminReviewRiskSummary(lookbackMinutes));
    }

    @GetMapping("/review-risk/timeline")
    public ApiResponse<List<Map<String, Object>>> reviewRiskTimeline(
            @RequestParam(defaultValue = "60") int lookbackMinutes
    ) {
        return ApiResponse.ok(recycleApplicationService.adminReviewRiskTimeline(lookbackMinutes));
    }

    @GetMapping("/review-risk/top-listings")
    public ApiResponse<List<Map<String, Object>>> reviewRiskTopListings(
            @RequestParam(defaultValue = "60") int lookbackMinutes,
            @RequestParam(defaultValue = "10") int topN
    ) {
        return ApiResponse.ok(recycleApplicationService.adminReviewRiskTopListings(lookbackMinutes, topN));
    }

    @GetMapping("/review-strategy")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReviewStrategy(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        Map<String, Object> payload = recycleApplicationService.adminGetReviewStrategyConfig();
        return buildCacheableResponse(payload, ifNoneMatch);
    }

    @PostMapping("/review-strategy/update")
    public ApiResponse<Map<String, Object>> updateReviewStrategy(@RequestBody UpdateReviewStrategyRequest request) {
        return ApiResponse.ok(recycleApplicationService.adminUpdateReviewStrategyConfig(
                request.updates(),
                request.operator(),
                toAuditContext(request.requestId(), request.changeSummary())
        ));
    }

    @GetMapping("/error-codes/global")
    public ResponseEntity<ApiResponse<Map<String, Object>>> globalErrorCodes(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        Map<String, Object> payload = recycleApplicationService.adminGlobalErrorCodeDictionary();
        return buildCacheableResponse(payload, ifNoneMatch);
    }

    @GetMapping("/degrade-actions/dictionary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> degradeActionDictionary(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        Map<String, Object> payload = recycleApplicationService.adminDegradeActionTypeDictionary();
        return buildCacheableResponse(payload, ifNoneMatch);
    }

    @GetMapping("/alert-noise-rules")
    public ResponseEntity<ApiResponse<Map<String, Object>>> alertNoiseRules(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        Map<String, Object> payload = recycleApplicationService.adminAlertNoiseRulesConfig();
        return buildCacheableResponse(payload, ifNoneMatch);
    }

    @PostMapping("/alert-noise-rules/update")
    public ApiResponse<Map<String, Object>> updateAlertNoiseRules(@RequestBody UpdateAlertNoiseRulesRequest request) {
        return ApiResponse.ok(recycleApplicationService.adminUpdateAlertNoiseRulesConfig(
                request.updates(),
                request.operator(),
                toAuditContext(request.requestId(), request.changeSummary())
        ));
    }

    @GetMapping("/config-center/bundle")
    public ResponseEntity<ApiResponse<Map<String, Object>>> configCenterBundle(
            @RequestParam(required = false) String clientVersion,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        Map<String, Object> payload = recycleApplicationService.adminConfigCenterBundle(clientVersion);
        return buildCacheableResponse(payload, ifNoneMatch);
    }

    @GetMapping("/config-center/module/{moduleName}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> configCenterModule(
            @PathVariable String moduleName,
            @RequestParam(required = false) String clientVersion,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        Map<String, Object> payload = recycleApplicationService.adminConfigCenterModule(moduleName, clientVersion);
        return buildCacheableResponse(payload, ifNoneMatch);
    }

    @GetMapping("/config-center/modules")
    public ResponseEntity<ApiResponse<Map<String, Object>>> configCenterModules(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        Map<String, Object> payload = recycleApplicationService.adminConfigCenterModules();
        return buildCacheableResponse(payload, ifNoneMatch);
    }

    @PostMapping("/config-center/module-diff")
    public ApiResponse<Map<String, Object>> configCenterModuleDiff(@RequestBody ModuleDiffRequest request) {
        return ApiResponse.ok(recycleApplicationService.adminConfigCenterModuleDiff(
                request.localDigests(),
                request.clientVersion()
        ));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> buildCacheableResponse(
            Map<String, Object> payload,
            String ifNoneMatch
    ) {
        String bodyJson = toJson(payload);
        String etag = "\"" + DigestUtils.md5DigestAsHex(bodyJson.getBytes(StandardCharsets.UTF_8)) + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, etag)
                    .build();
        }
        String lastModified = ((LocalDateTime) payload.get("updatedAt"))
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, etag)
                .header(HttpHeaders.LAST_MODIFIED, lastModified)
                .body(ApiResponse.ok(payload));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("异常码字典序列化失败");
        }
    }

    private AuditContext toAuditContext(String requestId, Map<String, Object> changeSummary) {
        String normalizedRequestId = requestId == null || requestId.isBlank()
                ? "req-" + UUID.randomUUID().toString().replace("-", "")
                : requestId.trim();
        return new AuditContext(normalizedRequestId, changeSummary);
    }

    public record ReviewOrderRequest(
            @NotBlank String orderNo,
            @NotBlank String action,
            String reviewedGrade
    ) {
    }

    public record PublishListingRequest(
            @NotBlank String recycleOrderNo,
            @NotNull BigDecimal salePrice,
            @Min(1) int stock
    ) {
    }

    public record DeliverResaleOrderRequest(
            @NotBlank String orderNo,
            String requestId,
            Map<String, Object> changeSummary
    ) {
    }

    public record RefundResaleOrderRequest(
            @NotBlank String orderNo,
            String requestId,
            Map<String, Object> changeSummary
    ) {
    }

    public record AutoConfirmReceiptRequest(
            @Min(1) Integer confirmAfterMinutes,
            @Min(1) Integer batchSize,
            String requestId,
            Map<String, Object> changeSummary
    ) {
    }

    public record ProcessReviewReportRequest(
            @NotNull Long reportId,
            @NotBlank String action,
            String processNote,
            String operator,
            String requestId,
            Map<String, Object> changeSummary
    ) {
    }

    public record ProcessReviewReportsBatchRequest(
            @NotNull List<Long> reportIds,
            @NotBlank String action,
            String processNote,
            String operator,
            String requestId,
            Map<String, Object> changeSummary
    ) {
    }

    public record UpdateReviewStrategyRequest(
            @NotNull Map<String, Object> updates,
            String operator,
            String requestId,
            Map<String, Object> changeSummary
    ) {
    }

    public record ModuleDiffRequest(
            Map<String, String> localDigests,
            String clientVersion
    ) {
    }

    public record UpdateAlertNoiseRulesRequest(
            @NotNull Map<String, Object> updates,
            String operator,
            String requestId,
            Map<String, Object> changeSummary
    ) {
    }
}
