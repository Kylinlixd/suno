package com.recycle.mall.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "provider.logistics", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockLogisticsProvider implements LogisticsProvider {

    @Override
    public String createTrackingNo(String recycleOrderNo) {
        return "TRK-" + recycleOrderNo;
    }

    @Override
    public String queryStatus(String trackingNo) {
        return "IN_TRANSIT";
    }
}
