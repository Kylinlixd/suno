
package com.suno.mall.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建评价请求
 */
public class CreateReviewRequest {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "上架商品ID不能为空")
    private Long listingId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低1")
    @Max(value = 5, message = "评分最高5")
    private Integer rating;

    @NotNull(message = "评价内容不能为空")
    @Size(min = 1, max = 512, message = "评价内容1-512字符")
    private String content;

    @Size(max = 1024, message = "图片URL最长1024字符")
    private String imageUrls;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getListingId() { return listingId; }
    public void setListingId(Long listingId) { this.listingId = listingId; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getImageUrls() { return imageUrls; }
    public void setImageUrls(String imageUrls) { this.imageUrls = imageUrls; }
}
