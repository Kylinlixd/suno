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

import java.time.LocalDateTime;

@Entity
@Table(name = "suno_resale_review")
public class ResaleReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Nullable
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private ResaleOrderEntity order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private ResaleListingEntity listing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccountEntity user;

    @Column(nullable = false)
    private Integer rating;

    @Column(nullable = false, length = 512)
    private String content;

    @Column(length = 1024)
    @Nullable
    private String imageUrls;

    @Column(length = 512)
    @Nullable
    private String appendContent;

    @Column(length = 512)
    @Nullable
    private String merchantReply;

    @Column(nullable = false)
    private Boolean sensitiveHit;

    @Column(nullable = false, length = 32)
    private String moderationStatus;

    @Column
    @Nullable
    private LocalDateTime moderatedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    @Nullable
    private LocalDateTime appendedAt;

    @Column
    @Nullable
    private LocalDateTime repliedAt;

    @Nullable
    public Long getId() {
        return id;
    }

    public void setId(@Nullable Long id) {
        this.id = id;
    }

    public ResaleOrderEntity getOrder() {
        return order;
    }

    public void setOrder(ResaleOrderEntity order) {
        this.order = order;
    }

    public ResaleListingEntity getListing() {
        return listing;
    }

    public void setListing(ResaleListingEntity listing) {
        this.listing = listing;
    }

    public UserAccountEntity getUser() {
        return user;
    }

    public void setUser(UserAccountEntity user) {
        this.user = user;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Nullable
    public String getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(@Nullable String imageUrls) {
        this.imageUrls = imageUrls;
    }

    @Nullable
    public String getAppendContent() {
        return appendContent;
    }

    public void setAppendContent(@Nullable String appendContent) {
        this.appendContent = appendContent;
    }

    @Nullable
    public String getMerchantReply() {
        return merchantReply;
    }

    public void setMerchantReply(@Nullable String merchantReply) {
        this.merchantReply = merchantReply;
    }

    public Boolean getSensitiveHit() {
        return sensitiveHit;
    }

    public void setSensitiveHit(Boolean sensitiveHit) {
        this.sensitiveHit = sensitiveHit;
    }

    public String getModerationStatus() {
        return moderationStatus;
    }

    public void setModerationStatus(String moderationStatus) {
        this.moderationStatus = moderationStatus;
    }

    @Nullable
    public LocalDateTime getModeratedAt() {
        return moderatedAt;
    }

    public void setModeratedAt(@Nullable LocalDateTime moderatedAt) {
        this.moderatedAt = moderatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Nullable
    public LocalDateTime getAppendedAt() {
        return appendedAt;
    }

    public void setAppendedAt(@Nullable LocalDateTime appendedAt) {
        this.appendedAt = appendedAt;
    }

    @Nullable
    public LocalDateTime getRepliedAt() {
        return repliedAt;
    }

    public void setRepliedAt(@Nullable LocalDateTime repliedAt) {
        this.repliedAt = repliedAt;
    }
}
