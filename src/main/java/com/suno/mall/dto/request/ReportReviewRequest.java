
package com.suno.mall.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 举报评价请求
 */
public class ReportReviewRequest {

    @NotNull(message = "评价ID不能为空")
    private Long reviewId;

    @NotNull(message = "举报原因不能为空")
    @Size(min = 1, max = 256, message = "举报原因1-256字符")
    private String reason;

    public Long getReviewId() { return reviewId; }
    public void setReviewId(Long reviewId) { this.reviewId = reviewId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
