
package com.suno.mall.common;

import com.suno.mall.common.DatabaseOptimizationHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 数据库优化工具类测试
 */
public class DatabaseOptimizationHelperTest {

    private TestRepository repository;
    private List<TestEntity> testData;

    @BeforeEach
    public void setUp() {
        repository = mock(TestRepository.class);
        testData = Arrays.asList(
                new TestEntity(1L, "测试1"),
                new TestEntity(2L, "测试2"),
                new TestEntity(3L, "测试3"),
                new TestEntity(4L, "测试4"),
                new TestEntity(5L, "测试5")
        );
    }

    @Test
    public void testBatchFindByIds() {
        // 测试批量查询功能
        when(repository.findAllById(Arrays.asList(1L, 2L, 3L)))
                .thenReturn(Arrays.asList(testData.get(0), testData.get(1), testData.get(2)));

        List<TestEntity> result = DatabaseOptimizationHelper.batchFindByIds(
                repository, Arrays.asList(1L, 2L, 3L), 3, TestEntity::getId);

        assertEquals(3, result.size());
        assertEquals("测试1", result.get(0).getName());
        assertEquals("测试2", result.get(1).getName());
        assertEquals("测试3", result.get(2).getName());
    }

@Test
public void testBatchFindByIdsBatchSize() {
    // 测试分批查询功能 【正确的分批：2个一组】
    when(repository.findAllById(Arrays.asList(1L, 2L)))
            .thenReturn(Arrays.asList(testData.get(0), testData.get(1)));
    when(repository.findAllById(Arrays.asList(3L, 4L)))
            .thenReturn(Arrays.asList(testData.get(2), testData.get(3)));
    when(repository.findAllById(Arrays.asList(5L)))
            .thenReturn(Arrays.asList(testData.get(4)));

    List<TestEntity> result = DatabaseOptimizationHelper.batchFindByIds(
            repository, Arrays.asList(1L, 2L, 3L, 4L, 5L), 2, TestEntity::getId);

    assertEquals(5, result.size());
    assertEquals("测试1", result.get(0).getName());
    assertEquals("测试2", result.get(1).getName());
    assertEquals("测试3", result.get(2).getName());
    assertEquals("测试4", result.get(3).getName());
    assertEquals("测试5", result.get(4).getName());
}

    @Test
    public void testBatchFindByIdsNotFound() {
        // 测试查询不到记录的情况
        when(repository.findAllById(Arrays.asList(1L, 2L, 99L)))
                .thenReturn(Arrays.asList(testData.get(0), testData.get(1)));

        try {
            DatabaseOptimizationHelper.batchFindByIds(
                    repository, Arrays.asList(1L, 2L, 99L), 3, TestEntity::getId);
            fail("应该抛出异常");
        } catch (RuntimeException e) {
            assertNotNull(e.getMessage());
            assertTrue(e.getMessage().contains("未找到ID为 [99] 的记录"));
        }
    }

    @Test
    public void testCreatePageable() {
        // 测试创建分页对象
        Pageable pageable = DatabaseOptimizationHelper.createPageable(0, 10, Sort.by(Sort.Direction.DESC, "id"));

        assertEquals(0, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("id").getDirection());
    }

    @Test
    public void testCreatePageableInvalid() {
        // 测试创建无效分页对象
        assertThrows(IllegalArgumentException.class, () -> DatabaseOptimizationHelper.createPageable(-1, 10, Sort.unsorted()));
        assertThrows(IllegalArgumentException.class, () -> DatabaseOptimizationHelper.createPageable(0, -1, Sort.unsorted()));
    }

    @Test
    public void testWithOptimisticLockRetry() {
        // 测试乐观锁重试机制
        TestOperation operation = mock(TestOperation.class);

        // 第一次调用抛出异常，第二次成功
        when(operation.execute())
                .thenThrow(new ObjectOptimisticLockingFailureException("乐观锁冲突", new RuntimeException("测试异常")))
                .thenReturn("成功");

        String result = DatabaseOptimizationHelper.withOptimisticLockRetry(operation, 3);

        assertEquals("成功", result);
        verify(operation, times(2)).execute();
    }

    @Test
    public void testWithOptimisticLockRetryMaxRetries() {
        // 测试乐观锁重试达到最大次数
        TestOperation operation = mock(TestOperation.class);

        // 每次调用都抛出异常
        when(operation.execute())
                .thenThrow(new ObjectOptimisticLockingFailureException("乐观锁冲突", new RuntimeException("测试异常")))
                .thenThrow(new ObjectOptimisticLockingFailureException("乐观锁冲突", new RuntimeException("测试异常")))
                .thenThrow(new ObjectOptimisticLockingFailureException("乐观锁冲突", new RuntimeException("测试异常")));

        try {
            DatabaseOptimizationHelper.withOptimisticLockRetry(operation, 3);
            fail("应该抛出异常");
        } catch (RuntimeException e) {
            assertNotNull(e.getMessage());
            assertTrue(e.getMessage().contains("乐观锁重试失败，已达最大重试次数: 3"));
        }

        verify(operation, times(3)).execute();
    }

    // 测试用的实体类
    private static class TestEntity {
        private Long id;
        private String name;

        public TestEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    // 测试用的Repository接口
    private interface TestRepository extends JpaRepository<TestEntity, Long> {
    }

    // 测试用的操作接口
    private interface TestOperation extends DatabaseOptimizationHelper.OptimisticLockOperation<String> {
        // 继承 OptimisticLockOperation 接口
    }
}
