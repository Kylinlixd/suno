
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

    @InjectMocks
    private ResaleOrderService resaleOrderService;

    private UserAccountEntity testUser;
    private ResaleListingEntity testListing;
    private ResaleOrderEntity testOrder;

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

        ProductEntity product = new ProductEntity();
        product.setId(1L);
        product.setName("测试商品");

        testListing = new ResaleListingEntity();
        testListing.setId(1L);
        testListing.setSeller(testUser);
        testListing.setProduct(product);
        testListing.setPrice(new BigDecimal("99.99"));
        testListing.setStatus(Constants.LISTING_STATUS_ON_SHELF);
        testListing.setStock(10);

        testOrder = new ResaleOrderEntity();
        testOrder.setId(1L);
        testOrder.setBuyer(testUser);
        testOrder.setListing(testListing);
        testOrder.setQuantity(1);
        testOrder.setTotalAmount(new BigDecimal("99.99"));
        testOrder.setStatus("PENDING");
    }

    @Test
    public void testCreateResaleOrder() {
        // 测试创建转售订单
        when(resaleListingRepository.findById(1L)).thenReturn(Optional.of(testListing));
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(resaleOrderRepository.save(any())).thenReturn(testOrder);

        ResaleOrderEntity createdOrder = resaleOrderService.createResaleOrder(1L, 1L, 1);

        assertNotNull(createdOrder);
        assertEquals(testUser, createdOrder.getBuyer());
        assertEquals(testListing, createdOrder.getListing());
        assertEquals(1, createdOrder.getQuantity());
        assertEquals(new BigDecimal("99.99"), createdOrder.getTotalAmount());
        assertEquals("PENDING", createdOrder.getStatus());

        // 验证库存减少
        assertEquals(9, testListing.getStock());
    }

    @Test
    public void testCreateResaleOrderListingNotFound() {
        // 测试创建转售订单时商品不存在的情况
        when(resaleListingRepository.findById(1L)).thenReturn(Optional.empty());

        try {
            resaleOrderService.createResaleOrder(1L, 1L, 1);
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
