
package com.recycle.mall.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 订单状态流转请求
 */
public class TransitionOrderRequest {

    @NotNull(message = "订单号不能为空")
    private String orderNo;

    @NotNull(message = "动作不能为空")
    private String action;

    private String reviewedGrade;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getReviewedGrade() { return reviewedGrade; }
    public void setReviewedGrade(String reviewedGrade) { this.reviewedGrade = reviewedGrade; }
}
