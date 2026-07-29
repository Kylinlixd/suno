
package com.suno.mall.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 二销商品上架响应
 */
public class ResaleListingVO {

    private Long listingId;
    private Long recycleOrderId;
    private String brand;
    private String model;
    private String grade;
    private BigDecimal salePrice;
    private Integer stock;
    private String status;
    private Long favoriteCount;
    private LocalDateTime createdAt;

    public Long getListingId() { return listingId; }
    public void setListingId(Long listingId) { this.listingId = listingId; }
    public Long getRecycleOrderId() { return recycleOrderId; }
    public void setRecycleOrderId(Long recycleOrderId) { this.recycleOrderId = recycleOrderId; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getFavoriteCount() { return favoriteCount; }
    public void setFavoriteCount(Long favoriteCount) { this.favoriteCount = favoriteCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
