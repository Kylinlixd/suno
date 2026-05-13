
package com.suno.mall.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;


/**
 * 分页请求基类
 */
public class PageRequest {

    @Min(value = 0, message = "页码从0开始")
    private Integer page = 0;

    @Min(value = 1, message = "每页最少1条")
    @Max(value = 100, message = "每页最多100条")
    private Integer size = 20;

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }

    public org.springframework.data.domain.Pageable toSpringPageable() {
        return org.springframework.data.domain.PageRequest.of(
                page != null ? page : 0,
                size != null ? size : 20
        );
    }
}
