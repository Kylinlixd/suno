package com.recycle.mall.infrastructure.provider.logistics;

public interface LogisticsProvider {

    String createTrackingNo(String recycleOrderNo);

    String queryStatus(String trackingNo);
}
