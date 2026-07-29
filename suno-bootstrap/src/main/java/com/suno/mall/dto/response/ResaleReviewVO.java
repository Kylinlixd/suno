
package com.suno.mall.dto.response;

import java.time.LocalDateTime;

/**
 * 评价响应
 */
public class ResaleReviewVO {

    private Long id;
    private Long orderId;
    private Long listingId;
    private Long userId;
    private String username;
    private Integer rating;
    private String content;
    private String imageUrls;
    private String appendContent;
    private String merchantReply;
    private Boolean sensitiveHit;
    private String moderationStatus;
    private LocalDateTime moderatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime appendedAt;
    private LocalDateTime repliedAt;
    private Long voteCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getListingId() { return listingId; }
    public void setListingId(Long listingId) { this.listingId = listingId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getImageUrls() { return imageUrls; }
    public void setImageUrls(String imageUrls) { this.imageUrls = imageUrls; }
    public String getAppendContent() { return appendContent; }
    public void setAppendContent(String appendContent) { this.appendContent = appendContent; }
    public String getMerchantReply() { return merchantReply; }
    public void setMerchantReply(String merchantReply) { this.merchantReply = merchantReply; }
    public Boolean getSensitiveHit() { return sensitiveHit; }
    public void setSensitiveHit(Boolean sensitiveHit) { this.sensitiveHit = sensitiveHit; }
    public String getModerationStatus() { return moderationStatus; }
    public void setModerationStatus(String moderationStatus) { this.moderationStatus = moderationStatus; }
    public LocalDateTime getModeratedAt() { return moderatedAt; }
    public void setModeratedAt(LocalDateTime moderatedAt) { this.moderatedAt = moderatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getAppendedAt() { return appendedAt; }
    public void setAppendedAt(LocalDateTime appendedAt) { this.appendedAt = appendedAt; }
    public LocalDateTime getRepliedAt() { return repliedAt; }
    public void setRepliedAt(LocalDateTime repliedAt) { this.repliedAt = repliedAt; }
    public Long getVoteCount() { return voteCount; }
    public void setVoteCount(Long voteCount) { this.voteCount = voteCount; }
}
