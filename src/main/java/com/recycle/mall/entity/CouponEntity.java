
package com.recycle.mall.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板
 */
@Entity
@Table(name = "coupon")
public class CouponEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "coupon_type", nullable = false, length = 16)
    private String couponType = "FIXED";

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", precision = 12, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount;

    @Column(name = "remaining_count", nullable = false)
    private Integer remainingCount;

    @Column(name = "per_user_limit", nullable = false)
    private Integer perUserLimit = 1;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCouponType() { return couponType; }
    public void setCouponType(String couponType) { this.couponType = couponType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(BigDecimal minOrderAmount) { this.minOrderAmount = minOrderAmount; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getRemainingCount() { return remainingCount; }
    public void setRemainingCount(Integer remainingCount) { this.remainingCount = remainingCount; }
    public Integer getPerUserLimit() { return perUserLimit; }
    public void setPerUserLimit(Integer perUserLimit) { this.perUserLimit = perUserLimit; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
