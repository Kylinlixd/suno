package com.suno.mall.service;

import com.suno.mall.entity.RecycleOrderEntity;
import com.suno.mall.entity.ResaleOrderEntity;
import com.suno.mall.dao.RecycleOrderRepository;
import com.suno.mall.dao.ResaleOrderRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * M3 经营报表：GMV/转化/回收量/退款率 + 近 N 日趋势。
 * 当前实现为应用内聚合（JPA Specification 拉取后计算），数据量大后演进为预聚合表。
 */
@Service
public class AdminReportService {

    private final ResaleOrderRepository resaleOrderRepository;
    private final RecycleOrderRepository recycleOrderRepository;

    public AdminReportService(ResaleOrderRepository resaleOrderRepository,
                              RecycleOrderRepository recycleOrderRepository) {
        this.resaleOrderRepository = resaleOrderRepository;
        this.recycleOrderRepository = recycleOrderRepository;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> overview(Integer trendDays) {
        int days = trendDays == null || trendDays < 1 || trendDays > 90 ? 14 : trendDays;
        LocalDateTime trendStart = LocalDate.now().minusDays(days - 1L).atStartOfDay();

        List<ResaleOrderEntity> orders = resaleOrderRepository.findAll((Specification<ResaleOrderEntity>) (root, query, builder) ->
                builder.isNotNull(root.get("id")));
        long totalOrders = orders.size();
        long paidOrders = orders.stream().filter(order -> !"UNPAID".equals(order.getPayStatus())
                && !"CANCELLED".equals(order.getFulfillStatus())
                && !"AUTO_CLOSED".equals(order.getFulfillStatus())).count();
        long refundedOrders = orders.stream().filter(order -> "REFUNDED".equals(order.getPayStatus())).count();
        long completedOrders = orders.stream().filter(order -> "COMPLETED".equals(order.getFulfillStatus())).count();
        BigDecimal gmv = orders.stream()
                .filter(order -> "PAID".equals(order.getPayStatus()) || "REFUNDED".equals(order.getPayStatus()))
                .map(order -> order.getPayableAmount() != null ? order.getPayableAmount() : order.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundedAmount = orders.stream()
                .filter(order -> "REFUNDED".equals(order.getPayStatus()))
                .map(order -> order.getPayableAmount() != null ? order.getPayableAmount() : order.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long recycleCount = recycleOrderRepository.count();

        List<ResaleOrderEntity> trendOrders = resaleOrderRepository.findAll((Specification<ResaleOrderEntity>) (root, query, builder) ->
                builder.greaterThanOrEqualTo(root.get("createdAt"), trendStart));
        Map<LocalDate, long[]> dailyBuckets = new HashMap<>();
        for (ResaleOrderEntity order : trendOrders) {
            LocalDate day = order.getCreatedAt().toLocalDate();
            long[] bucket = dailyBuckets.computeIfAbsent(day, ignored -> new long[2]);
            bucket[0]++;
            if ("PAID".equals(order.getPayStatus()) || "REFUNDED".equals(order.getPayStatus())) {
                bucket[1]++;
            }
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int offset = days - 1; offset >= 0; offset--) {
            LocalDate day = LocalDate.now().minusDays(offset);
            long[] bucket = dailyBuckets.getOrDefault(day, new long[2]);
            Map<String, Object> point = new HashMap<>();
            point.put("date", day.toString());
            point.put("orders", bucket[0]);
            point.put("paidOrders", bucket[1]);
            trend.add(point);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("totalOrders", totalOrders);
        payload.put("paidOrders", paidOrders);
        payload.put("completedOrders", completedOrders);
        payload.put("refundedOrders", refundedOrders);
        payload.put("refundRate", totalOrders == 0 ? 0
                : BigDecimal.valueOf(refundedOrders * 100.0 / totalOrders).setScale(2, RoundingMode.HALF_UP).doubleValue());
        payload.put("gmv", gmv);
        payload.put("refundedAmount", refundedAmount);
        payload.put("recycleCount", recycleCount);
        payload.put("trendDays", days);
        payload.put("trend", trend);
        return payload;
    }
}
