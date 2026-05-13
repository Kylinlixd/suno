
package com.suno.mall.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 二销订单响应
 */
public class ResaleOrderVO {

    private Long id;
    private String orderNo;
    private Long buyerUserId;
    private String buyerUsername;
    private Long listingId;
    private BigDecimal amount;
    private String payStatus;
    private String fulfillStatus;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getBuyerUserId() { return buyerUserId; }
    public void setBuyerUserId(Long buyerUserId) { this.buyerUserId = buyerUserId; }
    public String getBuyerUsername() { return buyerUsername; }
    public void setBuyerUsername(String buyerUsername) { this.buyerUsername = buyerUsername; }
    public Long getListingId() { return listingId; }
    public void setListingId(Long listingId) { this.listingId = listingId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getPayStatus() { return payStatus; }
    public void setPayStatus(String payStatus) { this.payStatus = payStatus; }
    public String getFulfillStatus() { return fulfillStatus; }
    public void setFulfillStatus(String fulfillStatus) { this.fulfillStatus = fulfillStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
