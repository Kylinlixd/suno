package com.recycle.mall.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "valuation_rule")
public class ValuationRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String brand;

    @Column(nullable = false, length = 64)
    private String model;

    @Column(nullable = false)
    private Integer minMonths;

    @Column(nullable = false)
    private Integer maxMonths;

    @Column(nullable = false)
    private Integer minWearScore;

    @Column(nullable = false)
    private Integer maxWearScore;

    @Column(nullable = false, length = 32)
    private String grade;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getMinMonths() {
        return minMonths;
    }

    public void setMinMonths(Integer minMonths) {
        this.minMonths = minMonths;
    }

    public Integer getMaxMonths() {
        return maxMonths;
    }

    public void setMaxMonths(Integer maxMonths) {
        this.maxMonths = maxMonths;
    }

    public Integer getMinWearScore() {
        return minWearScore;
    }

    public void setMinWearScore(Integer minWearScore) {
        this.minWearScore = minWearScore;
    }

    public Integer getMaxWearScore() {
        return maxWearScore;
    }

    public void setMaxWearScore(Integer maxWearScore) {
        this.maxWearScore = maxWearScore;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
