
package com.suno.mall.service;

import com.suno.mall.service.support.AuditContext;
import com.suno.mall.service.support.VersionHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 配置中心服务（含字典、告警降噪规则、模块差异）
 */
@Service
public class ConfigCenterService {

    private static final Set<String> ALERT_NOISE_RULE_KEYS = Set.of(
            "allowlistKeys", "denylistKeys", "quietHoursStart", "quietHoursEnd", "quietHourPassLevels"
    );
    private static final String GLOBAL_ERROR_CODE_DICT_VERSION = "1.0.0";
    private static final LocalDateTime GLOBAL_ERROR_CODE_DICT_UPDATED_AT = LocalDateTime.of(2026, 4, 28, 23, 22, 0);
    private static final String ALERT_NOISE_RULES_VERSION = "1.0.0";
    private static final String CONFIG_CENTER_BUNDLE_VERSION = "1.0.0";
    private static final String DEGRADE_ACTION_DICT_VERSION = "1.0.0";
    private static final LocalDateTime DEGRADE_ACTION_DICT_UPDATED_AT = LocalDateTime.of(2026, 4, 29, 0, 3, 0);

    @Value("${mall.review.risk.level-high-sensitive-rate:0.25}")
    private double reviewRiskHighSensitiveRate;
    @Value("${mall.review.risk.level-medium-sensitive-rate:0.12}")
    private double reviewRiskMediumSensitiveRate;
    @Value("${mall.review.risk.level-high-pending-reports:10}")
    private int reviewRiskHighPendingReports;
    @Value("${mall.review.risk.level-medium-pending-reports:5}")
    private int reviewRiskMediumPendingReports;
    @Value("${mall.review.risk.level-high-total-reports:30}")
    private int reviewRiskHighTotalReports;
    @Value("${mall.review.risk.level-medium-total-reports:15}")
    private int reviewRiskMediumTotalReports;
    @Value("${mall.review.sort.deboost-penalty:25}")
    private double reviewSortDeboostPenalty;
    @Value("${mall.review.visibility.include-hidden-default:false}")
    private boolean reviewIncludeHiddenDefault;
    @Value("${mall.review.append-window-days:30}")
    private int reviewAppendWindowDays;
    @Value("${mall.config-center.module-diff-cache-window-seconds:30}")
    private int moduleDiffCacheWindowSeconds;

    private volatile LocalDateTime alertNoiseRulesUpdatedAt = LocalDateTime.of(2026, 4, 29, 15, 20, 0);
    private volatile List<String> alertNoiseAllowlistKeys = List.of("config.sync.consecutive_failures");
    private volatile List<String> alertNoiseDenylistKeys = List.of("config.sync.cache_miss");
    private volatile String alertNoiseQuietHoursStart = "00:00";
    private volatile String alertNoiseQuietHoursEnd = "07:00";
    private volatile List<String> alertNoiseQuietHourPassLevels = List.of("CRITICAL");

    private final ConcurrentMap<String, ModuleDiffCacheEntry> moduleDiffCache = new ConcurrentHashMap<>();

    private final ResaleReviewService resaleReviewService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public ConfigCenterService(
            ResaleReviewService resaleReviewService,
            AuditLogService auditLogService,
            ObjectMapper objectMapper
    ) {
        this.resaleReviewService = resaleReviewService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    // ========== 评价策略配置（委托给 ResaleReviewService） ==========

    @Transactional(readOnly = true)
    public Map<String, Object> adminGetReviewStrategyConfig() {
        return resaleReviewService.adminGetReviewStrategyConfig();
    }

    @Transactional
    public Map<String, Object> adminUpdateReviewStrategyConfig(Map<String, Object> updates, String operator, AuditContext auditContext) {
        return resaleReviewService.adminUpdateReviewStrategyConfig(updates, operator, auditContext);
    }

    // ========== 全局错误码字典 ==========

    @Transactional(readOnly = true)
    public Map<String, Object> adminGlobalErrorCodeDictionary() {
        List<Map<String, Object>> items = List.of(
                buildErrorCodeDictionaryItem("BAD_REQUEST", 400, "IllegalArgumentException", "请求参数不合法", "Invalid request parameters", "提示用户修正输入参数后重试", "Ask user to fix inputs and retry"),
                buildErrorCodeDictionaryItem("VALIDATION_FAILED", 400, "MethodArgumentNotValidException/ConstraintViolationException", "参数校验失败", "Validation failed", "展示字段错误并阻止提交", "Display field errors and block submission"),
                buildErrorCodeDictionaryItem("REQUEST_BODY_INVALID", 400, "HttpMessageNotReadableException", "请求体格式错误", "Malformed request body", "检查 JSON 结构与字段类型", "Check JSON structure and field types"),
                buildErrorCodeDictionaryItem("CONFLICT_OPTIMISTIC_LOCK", 409, "ObjectOptimisticLockingFailureException", "操作过于频繁，请重试", "Operation conflict, please retry", "前端退避后重试并刷新资源", "Retry with backoff and refresh resource"),
                buildErrorCodeDictionaryItem("CONFLICT_DATA_INTEGRITY", 409, "DataIntegrityViolationException", "数据冲突，请检查请求后重试", "Data conflict, please retry", "提示冲突原因并避免重复提交", "Show conflict reason and avoid duplicate submit"),
                buildErrorCodeDictionaryItem("INTERNAL_SERVER_ERROR", 500, "Exception", "系统异常，请稍后重试", "Internal server error, please retry later", "记录 traceId 并通知运维排查", "Record traceId and notify operators")
        );
        return Map.of("version", GLOBAL_ERROR_CODE_DICT_VERSION, "updatedAt", GLOBAL_ERROR_CODE_DICT_UPDATED_AT,
                "compatibility", Map.of("status", "STABLE", "minimumSupportedVersion", GLOBAL_ERROR_CODE_DICT_VERSION),
                "cacheHintSeconds", 300, "items", items);
    }

    // ========== 降级动作字典 ==========

    @Transactional(readOnly = true)
    public Map<String, Object> adminDegradeActionTypeDictionary() {
        List<Map<String, Object>> items = List.of(
                Map.of("type", "USE_CACHE_AND_RETRY", "description", "使用本地缓存并按 retryDelaySeconds 重试", "paramsSchema", Map.of("retryDelaySeconds", "integer")),
                Map.of("type", "USE_LOCAL_DEFAULT_REVIEW_STRATEGY", "description", "回退到本地默认评价策略", "paramsSchema", Map.of()),
                Map.of("type", "USE_LOCAL_ERROR_CODE_MAP", "description", "回退到本地错误码映射", "paramsSchema", Map.of()),
                Map.of("type", "SKIP_AND_USE_BASIC_HINTS", "description", "跳过高级策略提示并使用基础提示", "paramsSchema", Map.of())
        );
        return Map.of("version", DEGRADE_ACTION_DICT_VERSION, "updatedAt", DEGRADE_ACTION_DICT_UPDATED_AT,
                "compatibility", Map.of("status", "STABLE", "minimumSupportedVersion", DEGRADE_ACTION_DICT_VERSION),
                "cacheHintSeconds", 300, "items", items);
    }

    // ========== 告警降噪规则 ==========

    @Transactional(readOnly = true)
    public Map<String, Object> adminAlertNoiseRulesConfig() {
        List<Map<String, Object>> groupMeta = List.of(
                Map.of("displayOrder", 1, "groupKey", "alert.routing",
                        "titleI18n", Map.of("zh-CN", "路由规则", "en-US", "Routing Rules"),
                        "descriptionI18n", Map.of("zh-CN", "控制白名单与黑名单", "en-US", "Control allowlist and denylist")),
                Map.of("displayOrder", 2, "groupKey", "alert.quiet-hours",
                        "titleI18n", Map.of("zh-CN", "静默时间窗口", "en-US", "Quiet Hours"),
                        "descriptionI18n", Map.of("zh-CN", "控制静默时段放行级别", "en-US", "Control pass levels during quiet hours"))
        );
        List<Map<String, Object>> fieldMeta = List.of(
                Map.ofEntries(Map.entry("displayOrder", 1), Map.entry("groupKey", "alert.routing"), Map.entry("editable", true), Map.entry("key", "allowlistKeys"), Map.entry("type", "array"), Map.entry("uiComponentHint", "tags-input"), Map.entry("labelI18n", Map.of("zh-CN", "白名单键", "en-US", "Allowlist Keys")), Map.entry("descriptionI18n", Map.of("zh-CN", "始终放行的告警 key 列表", "en-US", "Alert keys always allowed")), Map.entry("validationMessageI18n", Map.of("zh-CN", "请输入字符串数组", "en-US", "Please provide a string array"))),
                Map.ofEntries(Map.entry("displayOrder", 2), Map.entry("groupKey", "alert.routing"), Map.entry("editable", true), Map.entry("key", "denylistKeys"), Map.entry("type", "array"), Map.entry("uiComponentHint", "tags-input"), Map.entry("labelI18n", Map.of("zh-CN", "黑名单键", "en-US", "Denylist Keys")), Map.entry("descriptionI18n", Map.of("zh-CN", "始终屏蔽的告警 key 列表", "en-US", "Alert keys always suppressed")), Map.entry("validationMessageI18n", Map.of("zh-CN", "请输入字符串数组", "en-US", "Please provide a string array"))),
                Map.ofEntries(Map.entry("displayOrder", 3), Map.entry("groupKey", "alert.quiet-hours"), Map.entry("editable", true), Map.entry("key", "quietHoursStart"), Map.entry("type", "string"), Map.entry("uiComponentHint", "time-input"), Map.entry("labelI18n", Map.of("zh-CN", "静默开始时间", "en-US", "Quiet Hours Start")), Map.entry("descriptionI18n", Map.of("zh-CN", "静默窗口开始时间（HH:mm）", "en-US", "Quiet window start time (HH:mm)")), Map.entry("validationMessageI18n", Map.of("zh-CN", "请输入 HH:mm 格式", "en-US", "Please use HH:mm format"))),
                Map.ofEntries(Map.entry("displayOrder", 4), Map.entry("groupKey", "alert.quiet-hours"), Map.entry("editable", true), Map.entry("key", "quietHoursEnd"), Map.entry("type", "string"), Map.entry("uiComponentHint", "time-input"), Map.entry("labelI18n", Map.of("zh-CN", "静默结束时间", "en-US", "Quiet Hours End")), Map.entry("descriptionI18n", Map.of("zh-CN", "静默窗口结束时间（HH:mm）", "en-US", "Quiet window end time (HH:mm)")), Map.entry("validationMessageI18n", Map.of("zh-CN", "请输入 HH:mm 格式", "en-US", "Please use HH:mm format"))),
                Map.ofEntries(Map.entry("displayOrder", 5), Map.entry("groupKey", "alert.quiet-hours"), Map.entry("editable", true), Map.entry("key", "quietHourPassLevels"), Map.entry("type", "array"), Map.entry("uiComponentHint", "multi-select"), Map.entry("options", List.of("INFO", "WARN", "CRITICAL")), Map.entry("labelI18n", Map.of("zh-CN", "静默时段放行级别", "en-US", "Quiet-Hour Pass Levels")), Map.entry("descriptionI18n", Map.of("zh-CN", "静默时段仍允许通知的告警级别", "en-US", "Levels still allowed during quiet hours")), Map.entry("validationMessageI18n", Map.of("zh-CN", "仅支持 INFO/WARN/CRITICAL", "en-US", "Only INFO/WARN/CRITICAL are allowed")))
        );
        return Map.ofEntries(
                Map.entry("version", ALERT_NOISE_RULES_VERSION), Map.entry("updatedAt", alertNoiseRulesUpdatedAt),
                Map.entry("compatibility", Map.of("status", "STABLE", "minimumSupportedVersion", ALERT_NOISE_RULES_VERSION)),
                Map.entry("cacheHintSeconds", 60),
                Map.entry("rules", Map.of("allowlistKeys", alertNoiseAllowlistKeys, "denylistKeys", alertNoiseDenylistKeys,
                        "quietHours", Map.of("start", alertNoiseQuietHoursStart, "end", alertNoiseQuietHoursEnd),
                        "quietHourPassLevels", alertNoiseQuietHourPassLevels)),
                Map.entry("groupMeta", groupMeta), Map.entry("fieldMeta", fieldMeta));
    }

    @Transactional
    public Map<String, Object> adminUpdateAlertNoiseRulesConfig(Map<String, Object> updates, String operator, AuditContext auditContext) {
        if (updates == null || updates.isEmpty()) throw new IllegalArgumentException("更新内容不能为空");
        for (String key : updates.keySet()) { if (!ALERT_NOISE_RULE_KEYS.contains(key)) throw new IllegalArgumentException("不支持的策略键: " + key); }
        synchronized (this) {
            if (updates.containsKey("allowlistKeys")) alertNoiseAllowlistKeys = parseStringList(updates.get("allowlistKeys"), "allowlistKeys");
            if (updates.containsKey("denylistKeys")) alertNoiseDenylistKeys = parseStringList(updates.get("denylistKeys"), "denylistKeys");
            if (updates.containsKey("quietHoursStart")) alertNoiseQuietHoursStart = parseTimeWindowValue(updates.get("quietHoursStart"), "quietHoursStart");
            if (updates.containsKey("quietHoursEnd")) alertNoiseQuietHoursEnd = parseTimeWindowValue(updates.get("quietHoursEnd"), "quietHoursEnd");
            if (updates.containsKey("quietHourPassLevels")) {
                List<String> levels = parseStringList(updates.get("quietHourPassLevels"), "quietHourPassLevels");
                for (String level : levels) { if (!Set.of("INFO", "WARN", "CRITICAL").contains(level)) throw new IllegalArgumentException("quietHourPassLevels 仅支持 INFO/WARN/CRITICAL"); }
                alertNoiseQuietHourPassLevels = levels;
            }
            alertNoiseRulesUpdatedAt = LocalDateTime.now();
        }
        String safeOperator = (operator == null || operator.isBlank()) ? "SYSTEM" : operator;
        auditLogService.logAction("ALERT_NOISE_RULES_UPDATE", "ALERT_NOISE_RULES", "ACTIVE", "operator=" + safeOperator + ",keys=" + updates.keySet() + auditLogService.buildAuditContextSuffix(auditContext));
        return adminAlertNoiseRulesConfig();
    }

    // ========== 配置中心 Bundle ==========

    @Transactional(readOnly = true)
    public Map<String, Object> adminConfigCenterBundle() { return adminConfigCenterBundle(null); }

    @Transactional(readOnly = true)
    public Map<String, Object> adminConfigCenterBundle(String clientVersion) {
        Map<String, Object> reviewStrategy = adminGetReviewStrategyConfig();
        Map<String, Object> errorCodes = adminGlobalErrorCodeDictionary();
        Map<String, Object> degradeActions = adminDegradeActionTypeDictionary();
        Map<String, Object> alertNoiseRules = adminAlertNoiseRulesConfig();
        LocalDateTime bundleUpdatedAt = resolveBundleUpdatedAt(reviewStrategy, errorCodes, degradeActions, alertNoiseRules);
        boolean explicitClientVersion = clientVersion != null && !clientVersion.isBlank();
        String safeClientVersion = VersionHelper.normalizeVersion(clientVersion);
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(buildBootstrapStep("FETCH_BUNDLE", 1, true, "1.0.0", "拉取 config-center/bundle 并记录 ETag", buildDegradeAction("USE_CACHE_AND_RETRY", Map.of("retryDelaySeconds", 30), "使用本地缓存并延迟重试拉取"), safeClientVersion));
        steps.add(buildBootstrapStep("APPLY_REVIEW_STRATEGY", 2, true, "1.0.0", "优先使用 bundle.modules.reviewStrategy 驱动评价展示配置", buildDegradeAction("USE_LOCAL_DEFAULT_REVIEW_STRATEGY", Map.of(), "回退到本地默认评价策略"), safeClientVersion));
        steps.add(buildBootstrapStep("APPLY_GLOBAL_ERROR_CODES", 3, true, "1.0.0", "使用 bundle.modules.globalErrorCodes 渲染错误提示与告警映射", buildDegradeAction("USE_LOCAL_ERROR_CODE_MAP", Map.of(), "回退到本地错误文案映射"), safeClientVersion));
        if (explicitClientVersion) { steps.add(buildBootstrapStep("APPLY_REVIEW_POLICY_HINTS", 4, false, "1.0.1", "应用高版本评价策略提示模块", buildDegradeAction("SKIP_AND_USE_BASIC_HINTS", Map.of(), "跳过该步骤并使用基础策略提示"), safeClientVersion)); }
        boolean allCompatible = steps.stream().allMatch(item -> Boolean.TRUE.equals(item.get("compatible")));
        long incompatibleRequiredCount = steps.stream().filter(item -> Boolean.TRUE.equals(item.get("required")) && Boolean.TRUE.equals(item.get("filtered"))).count();
        long incompatibleOptionalCount = steps.stream().filter(item -> !Boolean.TRUE.equals(item.get("required")) && Boolean.TRUE.equals(item.get("filtered"))).count();
        String compatibilityStatus = buildClientCompatibilityStatus(incompatibleRequiredCount, incompatibleOptionalCount);
        String compatibilityMessage = switch (compatibilityStatus) { case "UNSUPPORTED" -> "客户端版本过低，存在不兼容必需步骤，请升级客户端"; case "WARN" -> "客户端可运行，但存在不兼容可选步骤"; default -> "客户端版本兼容"; };
        String compatibilityRecommendedAction = switch (compatibilityStatus) { case "UNSUPPORTED" -> "升级客户端或降级使用兼容能力子集"; case "WARN" -> "继续执行必需步骤，并忽略不兼容可选步骤"; default -> "继续按 bootstrapPlan 执行初始化"; };
        return Map.ofEntries(
                Map.entry("version", CONFIG_CENTER_BUNDLE_VERSION), Map.entry("updatedAt", bundleUpdatedAt),
                Map.entry("compatibility", Map.of("status", compatibilityStatus, "minimumSupportedVersion", CONFIG_CENTER_BUNDLE_VERSION, "clientVersion", safeClientVersion, "allStepsCompatible", allCompatible, "errorCode", "UNSUPPORTED".equals(compatibilityStatus) ? "CLIENT_VERSION_UNSUPPORTED" : "NONE", "message", compatibilityMessage, "recommendedAction", compatibilityRecommendedAction)),
                Map.entry("cacheHintSeconds", 60),
                Map.entry("bootstrapPlan", Map.of("version", "1.0.0", "steps", steps, "cachePolicy", "命中 304 时继续使用本地缓存并跳过重渲染", "fallbackPolicy", "接口失败时回退到上次成功缓存；首次启动失败时使用内置默认策略")),
                Map.entry("moduleIndex", Map.of("reviewStrategy", buildModuleIndexEntry(reviewStrategy), "globalErrorCodes", buildModuleIndexEntry(errorCodes), "degradeActionTypes", buildModuleIndexEntry(degradeActions), "alertNoiseRules", buildModuleIndexEntry(alertNoiseRules))),
                Map.entry("modules", Map.of("reviewStrategy", reviewStrategy, "globalErrorCodes", errorCodes, "degradeActionTypes", degradeActions, "alertNoiseRules", alertNoiseRules)));
    }

    public Map<String, Object> adminConfigCenterModule(String moduleName, String clientVersion) {
        return switch (moduleName) {
            case "reviewStrategy" -> adminGetReviewStrategyConfig();
            case "globalErrorCodes" -> adminGlobalErrorCodeDictionary();
            case "degradeActionTypes" -> adminDegradeActionTypeDictionary();
            case "alertNoiseRules" -> adminAlertNoiseRulesConfig();
            case "bootstrapPlan" -> { Map<String, Object> bundle = adminConfigCenterBundle(clientVersion); yield Map.of("version", String.valueOf(bundle.get("version")), "updatedAt", bundle.get("updatedAt"), "compatibility", bundle.get("compatibility"), "cacheHintSeconds", bundle.getOrDefault("cacheHintSeconds", 60), "data", bundle.get("bootstrapPlan")); }
            default -> throw new IllegalArgumentException("不支持的模块名: " + moduleName);
        };
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminConfigCenterModules() {
        Map<String, Object> reviewStrategy = adminGetReviewStrategyConfig();
        Map<String, Object> globalErrorCodes = adminGlobalErrorCodeDictionary();
        Map<String, Object> degradeActionTypes = adminDegradeActionTypeDictionary();
        Map<String, Object> alertNoiseRules = adminAlertNoiseRulesConfig();
        Map<String, Object> bootstrapPlan = adminConfigCenterModule("bootstrapPlan", "1.0.0");
        return Map.of("version", "1.0.0", "updatedAt", LocalDateTime.of(2026, 4, 29, 10, 8, 0), "items", List.of(
                Map.of("name", "reviewStrategy", "description", "评价策略配置（含动态热更新参数）", "supportsClientVersion", false, "version", reviewStrategy.get("version"), "updatedAt", reviewStrategy.get("updatedAt"), "digest", buildModuleDigest(reviewStrategy)),
                Map.of("name", "globalErrorCodes", "description", "全局异常码字典", "supportsClientVersion", false, "version", globalErrorCodes.get("version"), "updatedAt", globalErrorCodes.get("updatedAt"), "digest", buildModuleDigest(globalErrorCodes)),
                Map.of("name", "degradeActionTypes", "description", "降级动作类型字典", "supportsClientVersion", false, "version", degradeActionTypes.get("version"), "updatedAt", degradeActionTypes.get("updatedAt"), "digest", buildModuleDigest(degradeActionTypes)),
                Map.of("name", "alertNoiseRules", "description", "告警降噪规则配置", "supportsClientVersion", false, "version", alertNoiseRules.get("version"), "updatedAt", alertNoiseRules.get("updatedAt"), "digest", buildModuleDigest(alertNoiseRules)),
                Map.of("name", "bootstrapPlan", "description", "启动步骤计划（按 clientVersion 计算兼容性）", "supportsClientVersion", true, "version", bootstrapPlan.get("version"), "updatedAt", bootstrapPlan.get("updatedAt"), "digest", buildModuleDigest(bootstrapPlan))));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminConfigCenterModuleDiff(Map<String, String> localDigests, String clientVersion) {
        String requestHash = buildModuleDiffRequestHash(localDigests, clientVersion);
        LocalDateTime now = LocalDateTime.now();
        cleanupExpiredModuleDiffCache(now);
        ModuleDiffCacheEntry cached = moduleDiffCache.get(requestHash);
        if (cached != null && cached.expireAt().isAfter(now)) { return mergeModuleDiffMeta(cached.result(), requestHash, true, cached.generatedAt()); }
        Map<String, Object> modules = adminConfigCenterModules();
        @SuppressWarnings("unchecked") List<Map<String, Object>> items = (List<Map<String, Object>>) modules.get("items");
        Map<String, String> safeLocalDigests = localDigests == null ? Map.of() : localDigests;
        List<Map<String, Object>> changed = new ArrayList<>(), unchanged = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String name = String.valueOf(item.get("name")); String serverDigest = String.valueOf(item.get("digest")); String localDigest = safeLocalDigests.get(name);
            Map<String, Object> diffItem = Map.of("name", name, "serverDigest", serverDigest, "localDigest", localDigest == null ? "" : localDigest, "supportsClientVersion", item.get("supportsClientVersion"));
            if (localDigest == null || !serverDigest.equals(localDigest)) changed.add(diffItem); else unchanged.add(diffItem);
        }
        Map<String, Object> result = Map.ofEntries(Map.entry("version", "1.0.0"), Map.entry("updatedAt", modules.get("updatedAt")), Map.entry("clientVersion", VersionHelper.normalizeVersion(clientVersion)), Map.entry("totalModules", items.size()), Map.entry("changedCount", changed.size()), Map.entry("unchangedCount", unchanged.size()), Map.entry("changed", changed), Map.entry("unchanged", unchanged));
        moduleDiffCache.put(requestHash, new ModuleDiffCacheEntry(now.plusSeconds(Math.max(1, moduleDiffCacheWindowSeconds)), now, result));
        return mergeModuleDiffMeta(result, requestHash, false, now);
    }

    // ========== 私有辅助 ==========

    private Map<String, Object> buildErrorCodeDictionaryItem(String errorCode, int httpStatus, String exceptionType, String defaultMessageZh, String defaultMessageEn, String recommendedActionZh, String recommendedActionEn) {
        return Map.ofEntries(Map.entry("errorCode", errorCode), Map.entry("httpStatus", httpStatus), Map.entry("exceptionType", exceptionType), Map.entry("defaultMessage", defaultMessageZh), Map.entry("defaultMessageI18n", Map.of("zh-CN", defaultMessageZh, "en-US", defaultMessageEn)), Map.entry("recommendedAction", recommendedActionZh), Map.entry("recommendedActionI18n", Map.of("zh-CN", recommendedActionZh, "en-US", recommendedActionEn)));
    }

    private Map<String, Object> buildBootstrapStep(String id, int order, boolean required, String minClientVersion, String action, Map<String, Object> degradeAction, String clientVersion) {
        boolean compatible = VersionHelper.compareVersion(clientVersion, minClientVersion) >= 0;
        return Map.ofEntries(Map.entry("id", id), Map.entry("order", order), Map.entry("required", required), Map.entry("minClientVersion", minClientVersion), Map.entry("action", action), Map.entry("degradeAction", degradeAction), Map.entry("compatible", compatible), Map.entry("filtered", !compatible));
    }

    private Map<String, Object> buildDegradeAction(String type, Map<String, Object> params, String message) {
        return Map.of("type", type, "params", params == null ? Map.of() : params, "message", message);
    }

    private String buildClientCompatibilityStatus(long incompatibleRequiredCount, long incompatibleOptionalCount) {
        if (incompatibleRequiredCount > 0) return "UNSUPPORTED"; if (incompatibleOptionalCount > 0) return "WARN"; return "STABLE";
    }

    private LocalDateTime resolveBundleUpdatedAt(Map<String, Object> reviewStrategy, Map<String, Object> errorCodes, Map<String, Object> degradeActions, Map<String, Object> alertNoiseRules) {
        LocalDateTime max = ((LocalDateTime) reviewStrategy.get("updatedAt")).isAfter((LocalDateTime) errorCodes.get("updatedAt")) ? (LocalDateTime) reviewStrategy.get("updatedAt") : (LocalDateTime) errorCodes.get("updatedAt");
        max = max.isAfter((LocalDateTime) degradeActions.get("updatedAt")) ? max : (LocalDateTime) degradeActions.get("updatedAt");
        return max.isAfter((LocalDateTime) alertNoiseRules.get("updatedAt")) ? max : (LocalDateTime) alertNoiseRules.get("updatedAt");
    }

    private Map<String, Object> buildModuleIndexEntry(Map<String, Object> module) {
        return Map.of("version", String.valueOf(module.getOrDefault("version", "UNKNOWN")), "updatedAt", module.getOrDefault("updatedAt", ""), "digest", buildModuleDigest(module));
    }

    private String buildModuleDigest(Map<String, Object> module) {
        try { return DigestUtils.md5DigestAsHex(objectMapper.writeValueAsString(module).getBytes(StandardCharsets.UTF_8)); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("模块索引摘要生成失败"); }
    }

    private String buildModuleDiffRequestHash(Map<String, String> localDigests, String clientVersion) {
        try { return DigestUtils.md5DigestAsHex(objectMapper.writeValueAsString(Map.of("clientVersion", VersionHelper.normalizeVersion(clientVersion), "localDigests", localDigests == null ? Map.of() : localDigests)).getBytes(StandardCharsets.UTF_8)); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("module-diff 请求摘要生成失败"); }
    }

    private void cleanupExpiredModuleDiffCache(LocalDateTime now) { moduleDiffCache.entrySet().removeIf(entry -> entry.getValue().expireAt().isBefore(now)); }

    private Map<String, Object> mergeModuleDiffMeta(Map<String, Object> base, String requestHash, boolean cacheHit, LocalDateTime generatedAt) {
        Map<String, Object> merged = new HashMap<>(base); merged.put("requestHash", requestHash); merged.put("cacheHit", cacheHit); merged.put("generatedAt", generatedAt); merged.put("cacheWindowSeconds", Math.max(1, moduleDiffCacheWindowSeconds)); return merged;
    }

    private List<String> parseStringList(Object raw, String fieldName) {
        if (!(raw instanceof List<?> list)) throw new IllegalArgumentException(fieldName + " 必须是字符串数组");
        List<String> values = new ArrayList<>(); for (Object item : list) { if (!(item instanceof String text)) throw new IllegalArgumentException(fieldName + " 必须是字符串数组"); String value = text.trim(); if (!value.isEmpty()) values.add(value); } return values;
    }

    private String parseTimeWindowValue(Object raw, String fieldName) {
        if (!(raw instanceof String text)) throw new IllegalArgumentException(fieldName + " 必须是 HH:mm 字符串");
        String value = text.trim(); if (!value.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) throw new IllegalArgumentException(fieldName + " 必须是 HH:mm 格式"); return value;
    }

    record ModuleDiffCacheEntry(LocalDateTime expireAt, LocalDateTime generatedAt, Map<String, Object> result) {}
}
