package com.recycle.mall.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {

    @GetMapping("/products/{id}.html")
    public String productDetail(@PathVariable String id, Model model) {
        model.addAttribute("productId", id);
        model.addAttribute("title", "二手商品详情页");
        model.addAttribute("price", "1599");
        model.addAttribute("grade", "良好");
        return "product-detail";
    }
}
