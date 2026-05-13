
package com.suno.mall.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 发布二销商品请求
 */
public class PublishResaleListingRequest {

    @NotNull(message = "回收单号不能为空")
    @Size(max = 64, message = "回收单号最长64字符")
    private String recycleOrderNo;

    @NotNull(message = "销售价格不能为空")
    private BigDecimal salePrice;

    @NotNull(message = "库存不能为空")
    @Min(value = 1, message = "库存至少为1")
    @Max(value = 9999, message = "库存最大9999")
    private Integer stock;

    public String getRecycleOrderNo() { return recycleOrderNo; }
    public void setRecycleOrderNo(String recycleOrderNo) { this.recycleOrderNo = recycleOrderNo; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
}
