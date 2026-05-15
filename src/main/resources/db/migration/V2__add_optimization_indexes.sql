
-- 添加优化索引
-- 回收订单状态和创建时间的联合索引
CREATE INDEX idx_recycle_order_status_created_at ON recycle_order(status, created_at);

-- 二销上架状态和创建时间的联合索引
CREATE INDEX idx_resale_listing_status_created_at ON resale_listing(status, created_at);

-- 二销订单状态和用户ID的联合索引
CREATE INDEX idx_resale_order_status_buyer_user_id ON resale_order(pay_status, buyer_user_id);
