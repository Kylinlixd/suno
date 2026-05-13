package com.suno.mall.service;

import com.suno.mall.provider.LogisticsProvider;
import org.springframework.stereotype.Service;

@Service
public class LogisticsService {

    private final LogisticsProvider logisticsProvider;

    public LogisticsService(LogisticsProvider logisticsProvider) {
        this.logisticsProvider = logisticsProvider;
    }

    public String createTrackingNo(String recycleOrderNo) {
        return logisticsProvider.createTrackingNo(recycleOrderNo);
    }

    public String queryStatus(String trackingNo) {
        return logisticsProvider.queryStatus(trackingNo);
    }
}
