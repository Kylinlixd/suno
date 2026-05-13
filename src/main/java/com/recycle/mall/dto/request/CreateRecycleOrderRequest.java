package com.recycle.mall.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建回收订单请求
 */
public class CreateRecycleOrderRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "SN码不能为空")
    @Size(max = 64, message = "SN码最长64字符")
    private String snCode;

    @NotBlank(message = "图片URL不能为空")
    @Size(max = 255, message = "图片URL最长255字符")
    private String imageUrl;

    @NotNull(message = "磨损分不能为空")
    @Min(value = 0, message = "磨损分最小为0")
    @Max(value = 100, message = "磨损分最大为100")
    private Integer wearScore;

    @Min(value = 0, message = "回收次数不能为负")
    private Integer recycleCount = 0;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getSnCode() { return snCode; }
    public void setSnCode(String snCode) { this.snCode = snCode; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Integer getWearScore() { return wearScore; }
    public void setWearScore(Integer wearScore) { this.wearScore = wearScore; }
    public Integer getRecycleCount() { return recycleCount; }
    public void setRecycleCount(Integer recycleCount) { this.recycleCount = recycleCount; }
}
