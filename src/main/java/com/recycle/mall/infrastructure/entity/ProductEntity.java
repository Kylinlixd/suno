package com.recycle.mall.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "product")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String snCode;

    @Column(nullable = false, length = 64)
    private String brand;

    @Column(nullable = false, length = 64)
    private String model;

    @Column(nullable = false)
    private LocalDate productionDate;

    @Column(nullable = false, length = 255)
    private String imageUrl;

    @Column(nullable = false)
    private Integer wearScore;

    @Column(nullable = false, length = 32)
    private String recycleGrade;

    @Column(nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal estimatedRecyclePrice;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSnCode() {
        return snCode;
    }

    public void setSnCode(String snCode) {
        this.snCode = snCode;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public LocalDate getProductionDate() {
        return productionDate;
    }

    public void setProductionDate(LocalDate productionDate) {
        this.productionDate = productionDate;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getRecycleGrade() {
        return recycleGrade;
    }

    public void setRecycleGrade(String recycleGrade) {
        this.recycleGrade = recycleGrade;
    }

    public Integer getWearScore() {
        return wearScore;
    }

    public void setWearScore(Integer wearScore) {
        this.wearScore = wearScore;
    }

    public java.math.BigDecimal getEstimatedRecyclePrice() {
        return estimatedRecyclePrice;
    }

    public void setEstimatedRecyclePrice(java.math.BigDecimal estimatedRecyclePrice) {
        this.estimatedRecyclePrice = estimatedRecyclePrice;
    }
}
