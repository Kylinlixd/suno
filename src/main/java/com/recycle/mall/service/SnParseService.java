package com.recycle.mall.service;

import com.recycle.mall.entity.ProductDraft;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class SnParseService {

    public ProductDraft parse(String snCode, String imageUrl, int wearScore) {
        // TODO: 对接 SN 规则库或厂商接口，解析真实品牌/型号/生产日期
        return new ProductDraft(snCode, "DEMO_BRAND", "DEMO_MODEL", LocalDate.now().minusYears(2), imageUrl, wearScore);
    }
}
