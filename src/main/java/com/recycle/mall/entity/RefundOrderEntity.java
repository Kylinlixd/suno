
package com.recycle.mall.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单
 */
@Entity
@Table(name = "refund_order")
public class RefundOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_no", nullable = false, unique = true, length = 64)
    private String refundNo;

    @Column(name = "resale_order_id", nullable = false)
    private Long resaleOrderId;

    @Column(name = "buyer_user_id", nullable = false)
    private Long buyerUserId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "refund_channel", length = 32)
    private String refundChannel;

    @Column(name = "refund_transaction_no", length = 64)
    private String refundTransactionNo;

    @Column(name = "admin_remark", length = 512)
    private String adminRemark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRefundNo() { return refundNo; }
    public void setRefundNo(String refundNo) { this.refundNo = refundNo; }
    public Long getResaleOrderId() { return resaleOrderId; }
    public void setResaleOrderId(Long resaleOrderId) { this.resaleOrderId = resaleOrderId; }
    public Long getBuyerUserId() { return buyerUserId; }
    public void setBuyerUserId(Long buyerUserId) { this.buyerUserId = buyerUserId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRefundChannel() { return refundChannel; }
    public void setRefundChannel(String refundChannel) { this.refundChannel = refundChannel; }
    public String getRefundTransactionNo() { return refundTransactionNo; }
    public void setRefundTransactionNo(String refundTransactionNo) { this.refundTransactionNo = refundTransactionNo; }
    public String getAdminRemark() { return adminRemark; }
    public void setAdminRemark(String adminRemark) { this.adminRemark = adminRemark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}
