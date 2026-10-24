
package com.suno.mall.service;

import com.suno.mall.common.BizException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
    private com.suno.mall.dao.UserAddressRepository userAddressRepository;

    @Mock
    private com.suno.mall.dao.RefundOrderRepository refundOrderRepository;

    @Mock
    private CouponService couponService;

    @Mock
    private CartService cartService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

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
        when(resaleListingRepository.reserveOneStock(eq(1L), eq("ON_SHELF"), eq("SOLD_OUT"), any(LocalDateTime.class)))
                .thenReturn(1);

        java.util.Map<String, Object> createdOrder = resaleOrderService.createResaleOrder(1L, 1L);

        assertNotNull(createdOrder);
        assertEquals(testListing.getId(), createdOrder.get("listingId"));
        assertEquals(new BigDecimal("99.99"), createdOrder.get("amount"));
        assertEquals("UNPAID", createdOrder.get("payStatus"));
        assertEquals("WAIT_PAY", createdOrder.get("fulfillStatus"));
        verify(resaleOrderRepository).save(any(ResaleOrderEntity.class));
        verify(resaleListingRepository).reserveOneStock(eq(1L), eq("ON_SHELF"), eq("SOLD_OUT"), any(LocalDateTime.class));
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
    public void testMarkPaidRejectsLatePaymentAfterStatusChanged() {
        ResaleOrderEntity order = order("B2C-LATE01", "UNPAID", "WAIT_PAY", testUser);
        when(resaleOrderRepository.findWithDetailsByOrderNo("B2C-LATE01")).thenReturn(Optional.of(order));
        when(resaleOrderRepository.compareAndSetStatus(
                "B2C-LATE01", "UNPAID", "WAIT_PAY", "PAID", "TO_DELIVER")).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> resaleOrderService.markResaleOrderPaid("B2C-LATE01"));

        assertEquals(ErrorCode.ORDER_STATUS_CONFLICT, ex.getErrorCode());
        verify(auditLogService, never()).logAction(eq("RESALE_ORDER_PAY"), any(), any(), any());
    }

    @Test
    public void testCancelReleasesStockOnlyAfterCasSuccess() {
        ResaleOrderEntity order = order("B2C-CANCEL01", "UNPAID", "WAIT_PAY", testUser);
        when(resaleOrderRepository.findWithDetailsByOrderNo("B2C-CANCEL01")).thenReturn(Optional.of(order));
        when(resaleOrderRepository.compareAndSetStatus(
                "B2C-CANCEL01", "UNPAID", "WAIT_PAY", "UNPAID", "CANCELLED")).thenReturn(1);
        when(resaleListingRepository.releaseOneStock(eq(1L), eq("ON_SHELF"), eq("SOLD_OUT"), any(LocalDateTime.class))).thenReturn(1);

        java.util.Map<String, Object> result = resaleOrderService.cancelUnpaidResaleOrder("B2C-CANCEL01", 1L);

        assertEquals("CANCELLED", result.get("fulfillStatus"));
        verify(resaleListingRepository).releaseOneStock(eq(1L), eq("ON_SHELF"), eq("SOLD_OUT"), any(LocalDateTime.class));
    }

    @Test
    public void testCancelRejectsOrderOwnedByOtherBuyer() {
        UserAccountEntity otherBuyer = new UserAccountEntity();
        otherBuyer.setId(2L);
        otherBuyer.setUsername("other");
        ResaleOrderEntity order = order("B2C-CANCEL02", "UNPAID", "WAIT_PAY", otherBuyer);
        when(resaleOrderRepository.findWithDetailsByOrderNo("B2C-CANCEL02")).thenReturn(Optional.of(order));

        BizException ex = assertThrows(BizException.class,
                () -> resaleOrderService.cancelUnpaidResaleOrder("B2C-CANCEL02", 1L));

        assertEquals(ErrorCode.ORDER_NOT_OWNER, ex.getErrorCode());
        verify(resaleOrderRepository, never()).compareAndSetStatus(any(), any(), any(), any(), any());
        verifyNoInteractions(resaleListingRepository);
    }

    @Test
    public void testListUsesDatabasePageTotal() {
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(testUser));
        Page<ResaleOrderEntity> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 7);
        when(resaleOrderRepository.pageByBuyer(eq(1L), eq(""), eq(""), any())).thenReturn(page);

        java.util.Map<String, Object> result = resaleOrderService.listBuyerResaleOrders(
                1L, null, null, null, null, null, 0, 20);

        assertEquals(7L, result.get("total"));
        assertEquals(List.of(), result.get("items"));
        verify(resaleOrderRepository).pageByBuyer(eq(1L), eq(""), eq(""), any());
    }

    private ResaleOrderEntity order(String orderNo, String payStatus, String fulfillStatus, UserAccountEntity buyer) {
        ResaleOrderEntity order = new ResaleOrderEntity();
        order.setOrderNo(orderNo);
        order.setBuyerUser(buyer);
        order.setListing(testListing);
        order.setAmount(new BigDecimal("99.99"));
        order.setPayStatus(payStatus);
        order.setFulfillStatus(fulfillStatus);
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }

    @Test
    public void testDetailReturnsSnapshotForOwner() {
        ProductEntity product = new ProductEntity();
        product.setId(10L);
        product.setBrand("Apple");
        product.setModel("iPhone 15");
        testListing.setProduct(product);
        ResaleOrderEntity order = order("B2C-DETAIL01", "PAID", "DELIVERED", testUser);
        when(resaleOrderRepository.findWithDetailsByOrderNo("B2C-DETAIL01")).thenReturn(Optional.of(order));
        when(resaleReviewRepository.findByOrder_OrderNoAndUser_Id("B2C-DETAIL01", 1L)).thenReturn(Optional.empty());

        java.util.Map<String, Object> detail = resaleOrderService.getBuyerResaleOrderDetail("B2C-DETAIL01", 1L);

        assertEquals("B2C-DETAIL01", detail.get("orderNo"));
        assertEquals("Apple", detail.get("productBrand"));
        assertEquals("iPhone 15", detail.get("productModel"));
        assertEquals(true, detail.get("canConfirmReceipt"));
        assertEquals(false, detail.get("canCreateReview"));
    }

    @Test
    public void testDetailRejectsOtherBuyer() {
        ProductEntity product = new ProductEntity();
        product.setId(10L);
        product.setBrand("Apple");
        product.setModel("iPhone 15");
        testListing.setProduct(product);
        UserAccountEntity otherBuyer = new UserAccountEntity();
        otherBuyer.setId(2L);
        otherBuyer.setUsername("other");
        ResaleOrderEntity order = order("B2C-DETAIL02", "PAID", "DELIVERED", otherBuyer);
        when(resaleOrderRepository.findWithDetailsByOrderNo("B2C-DETAIL02")).thenReturn(Optional.of(order));

        BizException ex = assertThrows(BizException.class,
                () -> resaleOrderService.getBuyerResaleOrderDetail("B2C-DETAIL02", 1L));

        assertEquals(ErrorCode.ORDER_NOT_OWNER, ex.getErrorCode());
        verifyNoInteractions(resaleReviewRepository);
    }

    @Test
    public void testAutoCloseStopsWhenNoCasProgress() {
        ResaleOrderEntity order = order("B2C-AUTOCLOSE01", "UNPAID", "WAIT_PAY", testUser);
        when(resaleOrderRepository.findByPayStatusAndFulfillStatusAndCreatedAtBefore(
                eq("UNPAID"), eq("WAIT_PAY"), any(LocalDateTime.class), any()))
                .thenReturn(List.of(order));
        when(resaleOrderRepository.compareAndSetStatus(
                "B2C-AUTOCLOSE01", "UNPAID", "WAIT_PAY", "UNPAID", "AUTO_CLOSED")).thenReturn(0);

        int closed = resaleOrderService.autoCloseExpiredUnpaidOrders(15, 20);

        assertEquals(0, closed);
        verify(resaleOrderRepository, times(1)).compareAndSetStatus(
                "B2C-AUTOCLOSE01", "UNPAID", "WAIT_PAY", "UNPAID", "AUTO_CLOSED");
        verify(resaleListingRepository, never()).releaseOneStock(any(), any(), any(), any());
    }

    @Test
    public void testConstantsInitialization() {
        // 测试常量初始化
        assertEquals("ON_SHELF", Constants.LISTING_STATUS_ON_SHELF);
        assertEquals("SOLD_OUT", Constants.LISTING_STATUS_SOLD_OUT);
        assertEquals(30, Constants.DEFAULT_REVIEW_APPEND_WINDOW_DAYS);
    }
}
