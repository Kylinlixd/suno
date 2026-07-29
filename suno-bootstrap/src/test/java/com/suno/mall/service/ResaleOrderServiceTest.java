
package com.suno.mall.service;

import com.suno.mall.common.Constants;
import com.suno.mall.common.ErrorCode;
import com.suno.mall.dao.UserAccountRepository;
import com.suno.mall.dao.ResaleListingRepository;
import com.suno.mall.dao.ResaleOrderRepository;
import com.suno.mall.dao.ResaleReviewRepository;
import com.suno.mall.dao.PaymentIdempotencyRepository;
import com.suno.mall.entity.UserAccountEntity;
import com.suno.mall.entity.ResaleListingEntity;
import com.suno.mall.entity.ResaleOrderEntity;
import com.suno.mall.entity.ProductEntity;
import com.suno.mall.entity.ResaleReviewEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 转售订单服务测试
 */
@ExtendWith(MockitoExtension.class)
public class ResaleOrderServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private ResaleListingRepository resaleListingRepository;

    @Mock
    private ResaleOrderRepository resaleOrderRepository;

    @Mock
    private ResaleReviewRepository resaleReviewRepository;

    @Mock
    private PaymentIdempotencyRepository paymentIdempotencyRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ResaleListingService resaleListingService;

    @InjectMocks
    private ResaleOrderService resaleOrderService;

    private UserAccountEntity testUser;
    private ResaleListingEntity testListing;

    @BeforeEach
    public void setUp() {
        // 验证常量是否正确加载
        assertEquals("ON_SHELF", Constants.LISTING_STATUS_ON_SHELF);
        assertEquals("SOLD_OUT", Constants.LISTING_STATUS_SOLD_OUT);
        assertEquals(30, Constants.DEFAULT_REVIEW_APPEND_WINDOW_DAYS);

        // 初始化测试数据
        testUser = new UserAccountEntity();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        testListing = new ResaleListingEntity();
        testListing.setId(1L);
        testListing.setSalePrice(new BigDecimal("99.99"));
        testListing.setStatus(Constants.LISTING_STATUS_ON_SHELF);
        testListing.setStock(10);
    }

    @Test
    public void testCreateResaleOrder() {
        // 测试创建转售订单
        when(resaleListingRepository.findWithDetailsById(1L)).thenReturn(Optional.of(testListing));
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(testUser));

        java.util.Map<String, Object> createdOrder = resaleOrderService.createResaleOrder(1L, 1L);

        assertNotNull(createdOrder);
        assertEquals(testListing.getId(), createdOrder.get("listingId"));
        assertEquals(new BigDecimal("99.99"), createdOrder.get("amount"));
        assertEquals("UNPAID", createdOrder.get("payStatus"));
        assertEquals("WAIT_PAY", createdOrder.get("fulfillStatus"));
        verify(resaleOrderRepository).save(any(ResaleOrderEntity.class));

        // 验证库存减少
        assertEquals(9, testListing.getStock());
    }

    @Test
    public void testCreateResaleOrderListingNotFound() {
        // 测试创建转售订单时商品不存在的情况
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(resaleListingRepository.findWithDetailsById(1L)).thenReturn(Optional.empty());

        try {
            resaleOrderService.createResaleOrder(1L, 1L);
            fail("应该抛出异常");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("商品不存在"));
        }
    }

    @Test
    public void testConstantsInitialization() {
        // 测试常量初始化
        assertEquals("ON_SHELF", Constants.LISTING_STATUS_ON_SHELF);
        assertEquals("SOLD_OUT", Constants.LISTING_STATUS_SOLD_OUT);
        assertEquals(30, Constants.DEFAULT_REVIEW_APPEND_WINDOW_DAYS);
    }
}
