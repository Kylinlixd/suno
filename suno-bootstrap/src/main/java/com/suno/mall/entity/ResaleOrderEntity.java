package com.suno.mall.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "suno_resale_order")
public class ResaleOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Nullable
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_user_id", nullable = false)
    private UserAccountEntity buyerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private ResaleListingEntity listing;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 32)
    private String payStatus;

    @Column(nullable = false, length = 32)
    private String fulfillStatus;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // ==================== M2 履约与优惠 ====================

    /** 收货人姓名快照（下单时从地址复制，地址后续修改不影响订单） */
    @Column(name = "receiver_name", length = 64)
    private String receiverName;

    @Column(name = "receiver_phone", length = 32)
    private String receiverPhone;

    /** 收货地址全文快照：省 市 区 详细地址 */
    @Column(name = "receiver_address", length = 320)
    private String receiverAddress;

    /** 核销的用户券 id（suno_coupon_user.id） */
    @Column(name = "coupon_user_id")
    private Long couponUserId;

    /** 券抵扣金额，无券为 0 */
    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** 应付金额 = amount - discountAmount */
    @Column(name = "payable_amount", precision = 12, scale = 2)
    private BigDecimal payableAmount;

    /** 支付渠道：MOCK / ALIPAY */
    @Column(name = "pay_channel", length = 32)
    private String payChannel;

    /** 网关交易号（支付宝 trade_no） */
    @Column(name = "trade_no", length = 64)
    private String tradeNo;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** P1 积分抵现：本单消耗的积分数量与抵扣金额 */
    @Column(name = "points_used")
    private Integer pointsUsed;

    @Column(name = "points_deduct_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal pointsDeductAmount = BigDecimal.ZERO;

    /** P1 发货物流单号 */
    @Column(name = "tracking_no", length = 64)
    private String trackingNo;

    @Nullable
    public Long getId() {
        return id;
    }

    public void setId(@Nullable Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public UserAccountEntity getBuyerUser() {
        return buyerUser;
    }

    public void setBuyerUser(UserAccountEntity buyerUser) {
        this.buyerUser = buyerUser;
    }

    public ResaleListingEntity getListing() {
        return listing;
    }

    public void setListing(ResaleListingEntity listing) {
        this.listing = listing;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }

    public String getFulfillStatus() {
        return fulfillStatus;
    }

    public void setFulfillStatus(String fulfillStatus) {
        this.fulfillStatus = fulfillStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getReceiverAddress() {
        return receiverAddress;
    }

    public void setReceiverAddress(String receiverAddress) {
        this.receiverAddress = receiverAddress;
    }

    public Long getCouponUserId() {
        return couponUserId;
    }

    public void setCouponUserId(Long couponUserId) {
        this.couponUserId = couponUserId;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getPayableAmount() {
        return payableAmount;
    }

    public void setPayableAmount(BigDecimal payableAmount) {
        this.payableAmount = payableAmount;
    }

    public String getPayChannel() {
        return payChannel;
    }

    public void setPayChannel(String payChannel) {
        this.payChannel = payChannel;
    }

    public String getTradeNo() {
        return tradeNo;
    }

    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public Integer getPointsUsed() {
        return pointsUsed;
    }

    public void setPointsUsed(Integer pointsUsed) {
        this.pointsUsed = pointsUsed;
    }

    public BigDecimal getPointsDeductAmount() {
        return pointsDeductAmount;
    }

    public void setPointsDeductAmount(BigDecimal pointsDeductAmount) {
        this.pointsDeductAmount = pointsDeductAmount;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }
}
