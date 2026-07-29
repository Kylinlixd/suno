
package com.suno.mall.dto.response;


/**
 * 估值结果响应
 */
public class ValuationResultVO {

    private String grade;
    private java.math.BigDecimal price;

    public ValuationResultVO() {}

    public ValuationResultVO(String grade, java.math.BigDecimal price) {
        this.grade = grade;
        this.price = price;
    }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public java.math.BigDecimal getPrice() { return price; }
    public void setPrice(java.math.BigDecimal price) { this.price = price; }
}
