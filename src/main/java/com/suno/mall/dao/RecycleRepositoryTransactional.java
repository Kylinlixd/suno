package com.suno.mall.dao;

import com.suno.mall.config.RecycleTransactional;
import com.suno.mall.entity.RecycleOrderEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recycle Repository transaction boundary.
 * Provides unified transaction management for recycle repository operations.
 */
@Repository
public class RecycleRepositoryTransactional {

    private final RecycleOrderRepository recycleOrderRepository;

    public RecycleRepositoryTransactional(
            RecycleOrderRepository recycleOrderRepository) {
        this.recycleOrderRepository = recycleOrderRepository;
    }

    @RecycleTransactional
    public void saveOrder(RecycleOrderEntity order) {
        recycleOrderRepository.save(order);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public RecycleOrderEntity findOrder(String orderNo) {
        return recycleOrderRepository.findByOrderNo(orderNo);
    }
}
