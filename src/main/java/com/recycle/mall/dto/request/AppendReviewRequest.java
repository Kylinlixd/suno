
package com.recycle.mall.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 追加评价请求
 */
public class AppendReviewRequest {

    @NotNull(message = "评价ID不能为空")
    private Long reviewId;

    @NotNull(message = "追评内容不能为空")
    @Size(min = 1, max = 512, message = "追评内容1-512字符")
    private String appendContent;

    public Long getReviewId() { return reviewId; }
    public void setReviewId(Long reviewId) { this.reviewId = reviewId; }
    public String getAppendContent() { return appendContent; }
    public void setAppendContent(String appendContent) { this.appendContent = appendContent; }
}
