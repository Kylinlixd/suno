
package com.recycle.mall.dto.request;


import jakarta.validation.constraints.NotNull;


/**
 * 创建二销订单请求
 */
public class CreateResaleOrderRequest {

    @NotNull(message = "买家用户ID不能为空")
    private Long buyerUserId;

    @NotNull(message = "商品上架ID不能为空")
    private Long listingId;

    public Long getBuyerUserId() { return buyerUserId; }
    public void setBuyerUserId(Long buyerUserId) { this.buyerUserId = buyerUserId; }
    public Long getListingId() { return listingId; }
    public void setListingId(Long listingId) { this.listingId = listingId; }
}
