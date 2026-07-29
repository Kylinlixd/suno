
package com.suno.mall.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 商品信息响应
 */
public class ProductVO {

    private Long id;
    private String snCode;
    private String brand;
    private String model;
    private LocalDate productionDate;
    private String imageUrl;
    private Integer wearScore;
    private String recycleGrade;
    private BigDecimal estimatedRecyclePrice;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSnCode() { return snCode; }
    public void setSnCode(String snCode) { this.snCode = snCode; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public LocalDate getProductionDate() { return productionDate; }
    public void setProductionDate(LocalDate productionDate) { this.productionDate = productionDate; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Integer getWearScore() { return wearScore; }
    public void setWearScore(Integer wearScore) { this.wearScore = wearScore; }
    public String getRecycleGrade() { return recycleGrade; }
    public void setRecycleGrade(String recycleGrade) { this.recycleGrade = recycleGrade; }
    public BigDecimal getEstimatedRecyclePrice() { return estimatedRecyclePrice; }
    public void setEstimatedRecyclePrice(BigDecimal estimatedRecyclePrice) { this.estimatedRecyclePrice = estimatedRecyclePrice; }
}
