
package com.suno.mall.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数据库查询优化工具类
 */
public final class DatabaseOptimizationHelper {

    private DatabaseOptimizationHelper() {}

    /**
     * 分页查询优化方法，使用批量查询减少数据库访问次数
     * 
     * @param repository 数据库仓库
     * @param ids ID列表
     * @param batchSize 批量查询大小
     * @param idFunction 从实体中获取ID的函数
     * @param <T> 实体类型
     * @param <ID> ID类型
     * @return 批量查询结果列表
     */
    public static <T, ID> List<T> batchFindByIds(JpaRepository<T, ID> repository, List<ID> ids, int batchSize, 
            Function<T, ID> idFunction) {
        List<T> result = List.of();

        if (ids == null || ids.isEmpty()) {
            return result;
        }

        // 分批查询，避免一次性加载过多数据
        for (int i = 0; i < ids.size(); i += batchSize) {
            int end = Math.min(i + batchSize, ids.size());
            List<ID> batchIds = ids.subList(i, end);
            List<T> batchResult = repository.findAllById(batchIds);

            // 验证查询结果是否完整
            List<ID> foundIds = batchResult.stream()
                    .map(idFunction)
                    .collect(Collectors.toList());
            batchIds.removeAll(foundIds);

            if (!batchIds.isEmpty()) {
                throw new RuntimeException("未找到ID为 " + batchIds + " 的记录");
            }

            if (result.isEmpty()) {
                result = batchResult;
            } else {
                result.addAll(batchResult);
            }
        }

        return result;
    }

    /**
     * 创建优化的分页对象
     * 
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param sort 排序条件
     * @return 分页对象
     */
    public static Pageable createPageable(int page, int size, Sort sort) {
        if (size <= 0) {
            throw new IllegalArgumentException("每页大小必须大于0");
        }
        if (page < 0) {
            throw new IllegalArgumentException("页码必须大于等于0");
        }
        return PageRequest.of(page, size, sort);
    }

    /**
     * 创建优化的分页对象（默认按ID降序）
     * 
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 分页对象
     */
    public static Pageable createPageable(int page, int size) {
        return createPageable(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }

    /**
     * 乐观锁重试机制
     * 
     * @param operation 需要重试的操作
     * @param maxRetries 最大重试次数
     * @param <T> 返回类型
     * @return 操作结果
     */
    @Transactional
    public static <T> T withOptimisticLockRetry(OptimisticLockOperation<T> operation, int maxRetries) {
        int retryCount = 0;
        RuntimeException lastException = null;

        while (retryCount < maxRetries) {
            try {
                return operation.execute();
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
                retryCount++;
                lastException = ex;
                // 等待一段时间再重试，避免立即重试导致竞争
                try {
                    Thread.sleep(50 * retryCount);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试被中断", e);
                }
            }
        }

        throw new RuntimeException("乐观锁重试失败，已达最大重试次数: " + maxRetries, lastException);
    }

    /**
     * 乐观锁操作接口
     * 
     * @param <T> 返回类型
     */
    @FunctionalInterface
    public interface OptimisticLockOperation<T> {
        T execute();
    }
}
