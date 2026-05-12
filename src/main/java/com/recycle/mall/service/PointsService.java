package com.recycle.mall.service;

import org.springframework.stereotype.Service;

@Service
public class PointsService {

    public int calculateRecyclePoints(String userLevel, int recycleCount) {
        int base = 100;
        int levelBoost = "VIP".equalsIgnoreCase(userLevel) ? 80 : 20;
        int frequencyBoost = Math.min(recycleCount, 10) * 10;
        return base + levelBoost + frequencyBoost;
    }
}
