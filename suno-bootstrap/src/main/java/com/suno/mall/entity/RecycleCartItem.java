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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 回收购物车条目：用户暂存待回收旧物（扫码获取信息或手动填写），
 * 可连续添加多种品类，一次性提交为多笔回收单。与购买购物车同构，区别是"卖"。
 */
@Entity
@Table(name = "suno_recycle_cart")
public class RecycleCartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccountEntity user;

    /** ISBN 或条码 */
    @Column(name = "barcode", length = 64)
    private String barcode;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(name = "author", length = 256)
    private String author;

    @Column(name = "publisher", length = 256)
    private String publisher;

    @Column(name = "publish_year")
    private Integer publishYear;

    @Column(name = "category", length = 64)
    private String category;

    /** 成色评分 0-100（服务端/用户填写） */
    @Column(name = "wear_score", nullable = false)
    private Integer wearScore;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedPrice;

    /** 成色等级 GOOD/MEDIUM/UNQUALIFIED */
    @Column(nullable = false, length = 32)
    private String grade;

    /** 来源：SCAN（扫码获取）/ MANUAL（手动填写） */
    @Column(nullable = false, length = 32)
    private String source;

    /** 状态：IN_CART（在车）/ SUBMITTED（已提交） */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UserAccountEntity getUser() { return user; }
    public void setUser(UserAccountEntity user) { this.user = user; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public Integer getPublishYear() { return publishYear; }
    public void setPublishYear(Integer publishYear) { this.publishYear = publishYear; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getWearScore() { return wearScore; }
    public void setWearScore(Integer wearScore) { this.wearScore = wearScore; }
    public BigDecimal getEstimatedPrice() { return estimatedPrice; }
    public void setEstimatedPrice(BigDecimal estimatedPrice) { this.estimatedPrice = estimatedPrice; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
