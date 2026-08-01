package db.migration;

import java.sql.Types;
import java.util.List;
import java.util.Set;

final class CanonicalSchemaManifest {

    private CanonicalSchemaManifest() {
    }

    static List<TableSpec> tables() {
        return List.of(
                identityUserAccount(),
                identityUserAddress(),
                identityRefreshToken(),
                identityTokenBlacklist(),
                recycleCategory(),
                recycleProduct(),
                recycleProductImage(),
                recycleValuationRule(),
                recycleOrder(),
                recycleLogisticsTrack(),
                recyclePointsLedger(),
                marketplaceListing(),
                marketplaceFavorite(),
                marketplaceOrder(),
                marketplaceReview(),
                marketplaceReviewVote(),
                marketplaceReviewReport(),
                marketplaceCoupon(),
                marketplaceCouponUser(),
                marketplaceRefundOrder(),
                paymentIdempotency(),
                paymentReplayAutoHandleIdempotency(),
                paymentNonce(),
                paymentCallbackLog(),
                paymentReplayTask(),
                operationsAuditLog(),
                operationsExportTask(),
                operationsNotification()
        );
    }

    private static TableSpec identityUserAccount() {
        return table("suno_user_account",
                cols(
                        bigint("id", false, false, null, null),
                        varchar("username", 64, false),
                        varchar("password_hash", 128, false),
                        varchar("role_code", 32, false),
                        varchar("account_status", 32, false, "'ACTIVE'", "'ACTIVE'"),
                        varchar("level", 32, false),
                        integer("points", false, "0", "0")
                ),
                uniques(unique("uk_user_account_username", "username")),
                fks(),
                indexes()
        );
    }

    private static TableSpec identityUserAddress() {
        return table("suno_user_address",
                cols(
                        id(), bigint("user_id", false), varchar("receiver_name", 64, false),
                        varchar("receiver_phone", 32, false), varchar("province", 32, false),
                        varchar("city", 32, false), varchar("district", 32, false),
                        varchar("detail_address", 256, false),
                        bool("is_default", false, "0", "0"), timestamp("created_at", false),
                        timestamp("updated_at", true)
                ),
                uniques(),
                fks(fk("fk_user_address_user", "user_id", "suno_user_account", "id")),
                indexes(index("idx_user_address_user_id", "user_id"))
        );
    }

    private static TableSpec identityRefreshToken() {
        return table("suno_auth_refresh_token",
                cols(
                        id(), bigint("user_id", false), varchar("username", 64, false),
                        varchar("device_id", 64, false), varchar("token", 255, false),
                        timestamp("expire_at", false), bool("revoked", false, "0", "0"),
                        timestamp("created_at", false), timestamp("revoked_at", true)
                ),
                uniques(unique("uk_auth_refresh_token_token", "token")),
                fks(fk("fk_auth_refresh_token_user", "user_id", "suno_user_account", "id")),
                indexes(
                        index("idx_auth_refresh_token_user_id", "user_id"),
                        index("idx_auth_refresh_token_expire_at", "expire_at")
                )
        );
    }

    private static TableSpec identityTokenBlacklist() {
        return table("suno_auth_token_blacklist",
                cols(id(), varchar("jti", 64, false), varchar("username", 64, false),
                        timestamp("expire_at", false), timestamp("created_at", false)),
                uniques(unique("uk_auth_token_blacklist_jti", "jti")),
                fks(),
                indexes(index("idx_auth_token_blacklist_expire_at", "expire_at"))
        );
    }

    private static TableSpec recycleCategory() {
        return table("suno_category",
                cols(
                        id(), varchar("name", 64, false), bigint("parent_id", true),
                        varchar("icon_url", 255, true), integer("sort_order", false, "0", "0"),
                        varchar("status", 16, false, "'ENABLED'", "'ENABLED'"),
                        timestamp("created_at", false), timestamp("updated_at", true)
                ),
                uniques(), fks(),
                indexes(index("idx_category_parent_id", "parent_id"), index("idx_category_status", "status"))
        );
    }

    private static TableSpec recycleProduct() {
        return table("suno_product",
                cols(
                        id(), varchar("sn_code", 64, false), varchar("brand", 64, false),
                        varchar("model", 64, false), date("production_date", false),
                        varchar("image_url", 255, false), integer("wear_score", false),
                        varchar("recycle_grade", 32, false), decimal("estimated_recycle_price", false)
                ),
                uniques(unique("uk_product_sn_code", "sn_code")), fks(),
                indexes(
                        index("idx_product_brand_model", "brand", "model"),
                        index("idx_product_recycle_grade", "recycle_grade")
                )
        );
    }

    private static TableSpec recycleProductImage() {
        return table("suno_product_image",
                cols(
                        id(), bigint("product_id", false), varchar("image_url", 255, false),
                        integer("sort_order", false, "0", "0"),
                        varchar("image_type", 16, false, "'PRODUCT'", "'PRODUCT'"),
                        timestamp("created_at", false)
                ),
                uniques(), fks(fk("fk_product_image_product", "product_id", "suno_product", "id")),
                indexes(index("idx_product_image_product_id", "product_id"))
        );
    }

    private static TableSpec recycleValuationRule() {
        return table("suno_valuation_rule",
                cols(
                        id(), varchar("brand", 64, false), varchar("model", 64, false),
                        integer("min_months", false), integer("max_months", false),
                        integer("min_wear_score", false), integer("max_wear_score", false),
                        varchar("grade", 32, false), decimal("price", false)
                ),
                uniques(), fks(), indexes(index("idx_valuation_rule_brand_model", "brand", "model"))
        );
    }

    private static TableSpec recycleOrder() {
        return table("suno_recycle_order",
                cols(
                        id(), varchar("order_no", 64, false), bigint("user_id", false),
                        bigint("product_id", false), decimal("estimated_price", false),
                        varchar("grade", 32, false), varchar("status", 32, false),
                        timestamp("created_at", false)
                ),
                uniques(unique("uk_recycle_order_order_no", "order_no")),
                fks(
                        fk("fk_recycle_order_user", "user_id", "suno_user_account", "id"),
                        fk("fk_recycle_order_product", "product_id", "suno_product", "id")
                ),
                indexes(
                        index("idx_recycle_order_user_id", "user_id"),
                        index("idx_recycle_order_status", "status"),
                        index("idx_recycle_order_created_at", "created_at"),
                        index("idx_recycle_order_status_created_at", "status", "created_at")
                )
        );
    }

    private static TableSpec recycleLogisticsTrack() {
        return table("suno_logistics_track",
                cols(
                        id(), varchar("tracking_no", 64, false), bigint("order_id", false),
                        varchar("status", 32, false), timestamp("updated_at", false)
                ),
                uniques(unique("uk_logistics_track_tracking_no", "tracking_no")),
                fks(fk("fk_logistics_order", "order_id", "suno_recycle_order", "id")),
                indexes(index("idx_logistics_track_order_id", "order_id"))
        );
    }

    private static TableSpec recyclePointsLedger() {
        return table("suno_points_ledger",
                cols(
                        id(), bigint("user_id", false), integer("points_delta", false),
                        varchar("reason", 128, false), timestamp("created_at", false)
                ),
                uniques(), fks(fk("fk_points_user", "user_id", "suno_user_account", "id")),
                indexes(
                        index("idx_points_ledger_user_id", "user_id"),
                        index("idx_points_ledger_created_at", "created_at")
                )
        );
    }

    private static TableSpec marketplaceListing() {
        return table("suno_resale_listing",
                cols(
                        id(), bigint("recycle_order_id", false), bigint("product_id", false),
                        decimal("sale_price", false), integer("stock", false),
                        varchar("status", 32, false), timestamp("created_at", false),
                        timestamp("updated_at", false, null, "created_at"),
                        bigint("version", false, false, "0", "0")
                ),
                uniques(unique("uk_resale_listing_recycle_order", "recycle_order_id")),
                fks(
                        fk("fk_listing_recycle_order", "recycle_order_id", "suno_recycle_order", "id"),
                        fk("fk_listing_product", "product_id", "suno_product", "id")
                ),
                indexes(
                        index("idx_resale_listing_recycle_order_id", "recycle_order_id"),
                        index("idx_resale_listing_product_id", "product_id"),
                        index("idx_resale_listing_status", "status"),
                        index("idx_resale_listing_status_created_at", "status", "created_at")
                )
        );
    }

    private static TableSpec marketplaceFavorite() {
        return table("suno_resale_favorite",
                cols(id(), bigint("user_id", false), bigint("listing_id", false),
                        timestamp("created_at", false)),
                uniques(unique("uk_resale_favorite_user_listing", "user_id", "listing_id")),
                fks(
                        fk("fk_resale_favorite_user", "user_id", "suno_user_account", "id"),
                        fk("fk_resale_favorite_listing", "listing_id", "suno_resale_listing", "id")
                ),
                indexes(
                        index("idx_resale_favorite_user_id", "user_id"),
                        index("idx_resale_favorite_listing_id", "listing_id")
                )
        );
    }

    private static TableSpec marketplaceOrder() {
        return table("suno_resale_order",
                cols(
                        id(), varchar("order_no", 64, false), bigint("buyer_user_id", false),
                        bigint("listing_id", false), decimal("amount", false),
                        varchar("pay_status", 32, false), varchar("fulfill_status", 32, false),
                        timestamp("created_at", false)
                ),
                uniques(unique("uk_resale_order_order_no", "order_no")),
                fks(
                        fk("fk_resale_order_buyer", "buyer_user_id", "suno_user_account", "id"),
                        fk("fk_resale_order_listing", "listing_id", "suno_resale_listing", "id")
                ),
                indexes(
                        index("idx_resale_order_buyer_user_id", "buyer_user_id"),
                        index("idx_resale_order_listing_id", "listing_id"),
                        index("idx_resale_order_pay_fulfill", "pay_status", "fulfill_status"),
                        index("idx_resale_order_created_at", "created_at"),
                        index("idx_resale_order_status_buyer_user_id", "pay_status", "buyer_user_id")
                )
        );
    }

    private static TableSpec marketplaceReview() {
        return table("suno_resale_review",
                cols(
                        id(), bigint("order_id", false), bigint("listing_id", false), bigint("user_id", false),
                        integer("rating", false), varchar("content", 512, false),
                        varchar("image_urls", 1024, true), varchar("append_content", 512, true),
                        varchar("merchant_reply", 512, true), bool("sensitive_hit", false, "0", "0"),
                        varchar("moderation_status", 32, false, "'NORMAL'", "'NORMAL'"),
                        timestamp("moderated_at", true), timestamp("created_at", false),
                        timestamp("appended_at", true), timestamp("replied_at", true)
                ),
                uniques(unique("uk_resale_review_order_user", "order_id", "user_id")),
                fks(
                        fk("fk_resale_review_order", "order_id", "suno_resale_order", "id"),
                        fk("fk_resale_review_listing", "listing_id", "suno_resale_listing", "id"),
                        fk("fk_resale_review_user", "user_id", "suno_user_account", "id")
                ),
                indexes(
                        index("idx_resale_review_listing_id", "listing_id"),
                        index("idx_resale_review_user_id", "user_id"),
                        index("idx_resale_review_order_id", "order_id"),
                        index("idx_resale_review_moderation_status", "moderation_status")
                )
        );
    }

    private static TableSpec marketplaceReviewVote() {
        return table("suno_resale_review_vote",
                cols(id(), bigint("review_id", false), bigint("user_id", false),
                        timestamp("created_at", false)),
                uniques(unique("uk_resale_review_vote_review_user", "review_id", "user_id")),
                fks(
                        fk("fk_resale_review_vote_review", "review_id", "suno_resale_review", "id"),
                        fk("fk_resale_review_vote_user", "user_id", "suno_user_account", "id")
                ),
                indexes(
                        index("idx_resale_review_vote_review_id", "review_id"),
                        index("idx_resale_review_vote_user_id", "user_id")
                )
        );
    }

    private static TableSpec marketplaceReviewReport() {
        return table("suno_resale_review_report",
                cols(
                        id(), bigint("review_id", false), bigint("reporter_user_id", false),
                        varchar("reason", 256, false), varchar("status", 32, false),
                        varchar("process_note", 256, true), varchar("processed_by", 64, true),
                        timestamp("created_at", false), timestamp("processed_at", true)
                ),
                uniques(unique("uk_resale_review_report_review_user", "review_id", "reporter_user_id")),
                fks(
                        fk("fk_resale_review_report_review", "review_id", "suno_resale_review", "id"),
                        fk("fk_resale_review_report_user", "reporter_user_id", "suno_user_account", "id")
                ),
                indexes(
                        index("idx_resale_review_report_review_id", "review_id"),
                        index("idx_resale_review_report_status", "status")
                )
        );
    }

    private static TableSpec marketplaceCoupon() {
        return table("suno_coupon",
                cols(
                        id(), varchar("name", 64, false),
                        varchar("coupon_type", 16, false, "'FIXED'", "'FIXED'"),
                        decimal("discount_value", false), decimal("min_order_amount", true),
                        integer("total_count", false), integer("remaining_count", false),
                        integer("per_user_limit", false, "1", "1"), timestamp("start_time", false),
                        timestamp("end_time", false),
                        varchar("status", 16, false, "'ACTIVE'", "'ACTIVE'"),
                        timestamp("created_at", false)
                ),
                uniques(), fks(),
                indexes(index("idx_coupon_status", "status"), index("idx_coupon_end_time", "end_time"))
        );
    }

    private static TableSpec marketplaceCouponUser() {
        return table("suno_coupon_user",
                cols(
                        id(), bigint("user_id", false), bigint("coupon_id", false),
                        varchar("status", 16, false, "'UNUSED'", "'UNUSED'"),
                        timestamp("used_at", true), bigint("order_id", true), timestamp("expire_at", false),
                        timestamp("created_at", false)
                ),
                uniques(),
                fks(
                        fk("fk_coupon_user_user", "user_id", "suno_user_account", "id"),
                        fk("fk_coupon_user_coupon", "coupon_id", "suno_coupon", "id")
                ),
                indexes(
                        index("idx_coupon_user_user_id", "user_id"),
                        index("idx_coupon_user_coupon_id", "coupon_id"),
                        index("idx_coupon_user_status", "status")
                )
        );
    }

    private static TableSpec marketplaceRefundOrder() {
        return table("suno_refund_order",
                cols(
                        id(), varchar("refund_no", 64, false), bigint("resale_order_id", false),
                        bigint("buyer_user_id", false), decimal("amount", false),
                        varchar("reason", 512, true),
                        varchar("status", 32, false, "'PENDING'", "'PENDING'"),
                        varchar("refund_channel", 32, true), varchar("refund_transaction_no", 64, true),
                        varchar("admin_remark", 512, true), timestamp("created_at", false),
                        timestamp("processed_at", true)
                ),
                uniques(unique("uk_refund_order_refund_no", "refund_no")), fks(),
                indexes(
                        index("idx_refund_order_resale_order_id", "resale_order_id"),
                        index("idx_refund_order_buyer_user_id", "buyer_user_id"),
                        index("idx_refund_order_status", "status")
                )
        );
    }

    private static TableSpec paymentIdempotency() {
        return table("suno_payment_idempotency",
                cols(
                        id(), varchar("idempotency_key", 64, false), varchar("order_no", 64, false),
                        varchar("pay_status_snapshot", 32, false), timestamp("created_at", false)
                ),
                uniques(unique("uk_payment_idempotency_key", "idempotency_key")), fks(),
                indexes(index("idx_payment_idempotency_order_no", "order_no"))
        );
    }

    private static TableSpec paymentReplayAutoHandleIdempotency() {
        return table("suno_payment_replay_auto_handle_idempotency",
                cols(
                        id(), varchar("trace_id", 128, false), text("response_json", false),
                        timestamp("expire_at", false), timestamp("created_at", false)
                ),
                uniques(unique("uk_payment_replay_auto_trace", "trace_id")), fks(),
                indexes(index("idx_replay_auto_handle_idempotency_expire_at", "expire_at"))
        );
    }

    private static TableSpec paymentNonce() {
        return table("suno_payment_nonce",
                cols(id(), varchar("nonce", 128, false), timestamp("expire_at", false),
                        timestamp("created_at", false)),
                uniques(unique("uk_payment_nonce_nonce", "nonce")), fks(),
                indexes(index("idx_payment_nonce_expire_at", "expire_at"))
        );
    }

    private static TableSpec paymentCallbackLog() {
        return table("suno_payment_callback_log",
                cols(
                        id(), varchar("order_no", 64, false), varchar("idempotency_key", 64, false),
                        varchar("pay_status", 32, false), varchar("nonce", 128, false),
                        bigint("timestamp", false), varchar("signature", 128, false),
                        varchar("callback_status", 32, false), varchar("error_message", 512, true),
                        varchar("response_body", 255, false), varchar("source", 32, false),
                        integer("replay_count", false), timestamp("created_at", false),
                        timestamp("last_replay_at", true)
                ),
                uniques(), fks(),
                indexes(
                        index("idx_payment_callback_log_order_no", "order_no"),
                        index("idx_payment_callback_log_created_at", "created_at")
                )
        );
    }

    private static TableSpec paymentReplayTask() {
        return table("suno_payment_replay_task",
                cols(
                        id(), bigint("callback_log_id", false), varchar("status", 32, false),
                        integer("retry_count", false), varchar("last_error", 512, true),
                        timestamp("next_retry_at", false), timestamp("created_at", false),
                        timestamp("updated_at", true)
                ),
                uniques(), fks(),
                indexes(
                        index("idx_payment_replay_task_callback_status", "callback_log_id", "status"),
                        index("idx_payment_replay_task_status_next_retry", "status", "next_retry_at")
                )
        );
    }

    private static TableSpec operationsAuditLog() {
        return table("suno_operation_audit_log",
                cols(
                        id(), varchar("action_type", 64, false), varchar("target_type", 64, false),
                        varchar("target_id", 64, false), varchar("detail", 512, false),
                        timestamp("created_at", false)
                ),
                uniques(), fks(),
                indexes(
                        index("idx_audit_log_action_type", "action_type"),
                        index("idx_audit_log_target_type", "target_type"),
                        index("idx_audit_log_created_at", "created_at")
                )
        );
    }

    private static TableSpec operationsExportTask() {
        return table("suno_auth_export_task",
                cols(
                        id(), varchar("task_id", 64, false), varchar("idempotency_key", 128, true),
                        varchar("export_type", 32, false), varchar("export_format", 16, false),
                        varchar("status", 16, false), integer("retry_count", false, "0", "0"),
                        integer("max_retry", false, "2", "2"), varchar("file_name", 128, true),
                        varchar("content_text", 255, true), varchar("error_code", 64, true),
                        varchar("error_message", 512, true), timestamp("created_at", false),
                        timestamp("finished_at", true)
                ),
                uniques(unique("uk_auth_export_task_task_id", "task_id")), fks(),
                indexes(
                        index("idx_auth_export_task_status", "status"),
                        index("idx_auth_export_task_idempotency_key", "idempotency_key")
                )
        );
    }

    private static TableSpec operationsNotification() {
        return table("suno_notification",
                cols(
                        id(), bigint("user_id", false), varchar("title", 128, false),
                        varchar("content", 1024, false), varchar("notification_type", 32, false),
                        bigint("related_id", true), varchar("related_type", 32, true),
                        bool("is_read", false, "0", "0"), timestamp("created_at", false)
                ),
                uniques(), fks(),
                indexes(
                        index("idx_notification_user_read", "user_id", "is_read"),
                        index("idx_notification_created_at", "created_at")
                )
        );
    }

    private static TableSpec table(
            String name,
            List<ColumnSpec> columns,
            List<UniqueSpec> uniques,
            List<ForeignKeySpec> foreignKeys,
            List<IndexSpec> indexes
    ) {
        return new TableSpec(name, columns, List.of("id"), uniques, foreignKeys, indexes);
    }

    private static List<ColumnSpec> cols(ColumnSpec... columns) {
        return List.of(columns);
    }

    private static List<UniqueSpec> uniques(UniqueSpec... uniques) {
        return List.of(uniques);
    }

    private static List<ForeignKeySpec> fks(ForeignKeySpec... foreignKeys) {
        return List.of(foreignKeys);
    }

    private static List<IndexSpec> indexes(IndexSpec... indexes) {
        return List.of(indexes);
    }

    private static ColumnSpec id() {
        return bigint("id", false, true, null, null);
    }

    private static ColumnSpec bigint(String name, boolean nullable) {
        return bigint(name, nullable, false, null, null);
    }

    private static ColumnSpec bigint(
            String name,
            boolean nullable,
            boolean autoIncrement,
            String defaultExpression,
            String backfillExpression
    ) {
        return new ColumnSpec(name, "BIGINT", nullable, autoIncrement, defaultExpression,
                backfillExpression, Set.of(Types.BIGINT), 0, 0, 0);
    }

    private static ColumnSpec integer(String name, boolean nullable) {
        return integer(name, nullable, null, null);
    }

    private static ColumnSpec integer(
            String name,
            boolean nullable,
            String defaultExpression,
            String backfillExpression
    ) {
        return new ColumnSpec(name, "INT", nullable, false, defaultExpression,
                backfillExpression, Set.of(Types.INTEGER, Types.SMALLINT), 0, 0, 0);
    }

    private static ColumnSpec bool(
            String name,
            boolean nullable,
            String defaultExpression,
            String backfillExpression
    ) {
        return new ColumnSpec(name, "TINYINT(1)", nullable, false, defaultExpression,
                backfillExpression, Set.of(Types.TINYINT, Types.BIT, Types.BOOLEAN), 0, 0, 0);
    }

    private static ColumnSpec varchar(String name, int length, boolean nullable) {
        return varchar(name, length, nullable, null, null);
    }

    private static ColumnSpec varchar(
            String name,
            int length,
            boolean nullable,
            String defaultExpression,
            String backfillExpression
    ) {
        return new ColumnSpec(name, "VARCHAR(" + length + ")", nullable, false, defaultExpression,
                backfillExpression, Set.of(Types.VARCHAR, Types.NVARCHAR, Types.CHAR), length, 0, 0);
    }

    private static ColumnSpec text(String name, boolean nullable) {
        return new ColumnSpec(name, "TEXT", nullable, false, null, null,
                Set.of(Types.CLOB, Types.LONGVARCHAR, Types.LONGNVARCHAR), 0, 0, 0);
    }

    private static ColumnSpec decimal(String name, boolean nullable) {
        return new ColumnSpec(name, "DECIMAL(12, 2)", nullable, false, null, null,
                Set.of(Types.DECIMAL, Types.NUMERIC), 0, 12, 2);
    }

    private static ColumnSpec timestamp(String name, boolean nullable) {
        return timestamp(name, nullable, null, null);
    }

    private static ColumnSpec timestamp(
            String name,
            boolean nullable,
            String defaultExpression,
            String backfillExpression
    ) {
        return new ColumnSpec(name, "TIMESTAMP", nullable, false, defaultExpression,
                backfillExpression, Set.of(Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE), 0, 0, 0);
    }

    private static ColumnSpec date(String name, boolean nullable) {
        return new ColumnSpec(name, "DATE", nullable, false, null, null,
                Set.of(Types.DATE), 0, 0, 0);
    }

    private static UniqueSpec unique(String name, String... columns) {
        return new UniqueSpec(name, List.of(columns));
    }

    private static ForeignKeySpec fk(
            String name,
            String column,
            String referencedTable,
            String referencedColumn
    ) {
        return new ForeignKeySpec(name, List.of(column), referencedTable, List.of(referencedColumn));
    }

    private static IndexSpec index(String name, String... columns) {
        return new IndexSpec(name, List.of(columns));
    }

    record TableSpec(
            String name,
            List<ColumnSpec> columns,
            List<String> primaryKey,
            List<UniqueSpec> uniques,
            List<ForeignKeySpec> foreignKeys,
            List<IndexSpec> indexes
    ) {
    }

    record ColumnSpec(
            String name,
            String sqlType,
            boolean nullable,
            boolean autoIncrement,
            String defaultExpression,
            String backfillExpression,
            Set<Integer> acceptedJdbcTypes,
            int minimumLength,
            int precision,
            int scale
    ) {
    }

    record UniqueSpec(String name, List<String> columns) {
    }

    record ForeignKeySpec(
            String name,
            List<String> columns,
            String referencedTable,
            List<String> referencedColumns
    ) {
    }

    record IndexSpec(String name, List<String> columns) {
    }
}
