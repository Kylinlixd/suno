package com.recycle.mall.provider;

public interface LogisticsProvider {

    String createTrackingNo(String recycleOrderNo);

    String queryStatus(String trackingNo);
}
