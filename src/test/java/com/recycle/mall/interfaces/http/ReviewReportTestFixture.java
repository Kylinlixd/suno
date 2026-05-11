package com.recycle.mall.interfaces.http;

import com.recycle.mall.infrastructure.entity.ProductEntity;
import com.recycle.mall.infrastructure.entity.RecycleOrderEntity;
import com.recycle.mall.infrastructure.entity.ResaleListingEntity;
import com.recycle.mall.infrastructure.entity.ResaleOrderEntity;
import com.recycle.mall.infrastructure.entity.ResaleReviewEntity;
import com.recycle.mall.infrastructure.entity.ResaleReviewReportEntity;
import com.recycle.mall.infrastructure.entity.UserAccountEntity;
import com.recycle.mall.infrastructure.repository.ProductRepository;
import com.recycle.mall.infrastructure.repository.RecycleOrderRepository;
import com.recycle.mall.infrastructure.repository.ResaleListingRepository;
import com.recycle.mall.infrastructure.repository.ResaleOrderRepository;
import com.recycle.mall.infrastructure.repository.ResaleReviewReportRepository;
import com.recycle.mall.infrastructure.repository.ResaleReviewRepository;
import com.recycle.mall.infrastructure.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
class ReviewReportTestFixture {

    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private RecycleOrderRepository recycleOrderRepository;
    @Autowired
    private ResaleListingRepository resaleListingRepository;
    @Autowired
    private ResaleOrderRepository resaleOrderRepository;
    @Autowired
    private ResaleReviewRepository resaleReviewRepository;
    @Autowired
    private ResaleReviewReportRepository resaleReviewReportRepository;

    Long createPendingReport() {
        return createReport("PENDING", "NORMAL", false);
    }

    Long createPendingHiddenReviewReport() {
        return createReport("PENDING", "HIDDEN", false);
    }

    Long createApprovedDeboostReport() {
        return createReport("APPROVED", "DEBOOST", true);
    }

    private Long createReport(String reportStatus, String reviewModerationStatus, boolean processed) {
        UserAccountEntity alice = userAccountRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalArgumentException("alice not found"));
        UserAccountEntity bob = userAccountRepository.findByUsername("bob")
                .orElseThrow(() -> new IllegalArgumentException("bob not found"));

        ProductEntity product = new ProductEntity();
        product.setSnCode("SN-BATCH-" + UUID.randomUUID().toString().substring(0, 8));
        product.setBrand("TestBrand");
        product.setModel("Model-X");
        product.setProductionDate(LocalDate.of(2024, 1, 1));
        product.setImageUrl("https://example.com/image.jpg");
        product.setWearScore(90);
        product.setRecycleGrade("GOOD");
        product.setEstimatedRecyclePrice(new BigDecimal("1000.00"));
        productRepository.save(product);

        RecycleOrderEntity recycleOrder = new RecycleOrderEntity();
        recycleOrder.setOrderNo("RCY-BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        recycleOrder.setUser(alice);
        recycleOrder.setProduct(product);
        recycleOrder.setEstimatedPrice(new BigDecimal("1000.00"));
        recycleOrder.setGrade("GOOD");
        recycleOrder.setStatus("LISTED");
        recycleOrder.setCreatedAt(LocalDateTime.now());
        recycleOrderRepository.save(recycleOrder);

        ResaleListingEntity listing = new ResaleListingEntity();
        listing.setRecycleOrder(recycleOrder);
        listing.setProduct(product);
        listing.setSalePrice(new BigDecimal("1200.00"));
        listing.setStock(1);
        listing.setStatus("ON_SHELF");
        listing.setCreatedAt(LocalDateTime.now());
        resaleListingRepository.save(listing);

        ResaleOrderEntity resaleOrder = new ResaleOrderEntity();
        resaleOrder.setOrderNo("B2C-BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        resaleOrder.setBuyerUser(alice);
        resaleOrder.setListing(listing);
        resaleOrder.setAmount(new BigDecimal("1200.00"));
        resaleOrder.setPayStatus("PAID");
        resaleOrder.setFulfillStatus("DELIVERED");
        resaleOrder.setCreatedAt(LocalDateTime.now());
        resaleOrderRepository.save(resaleOrder);

        ResaleReviewEntity review = new ResaleReviewEntity();
        review.setOrder(resaleOrder);
        review.setListing(listing);
        review.setUser(alice);
        review.setRating(4);
        review.setContent("批量处理测试评价");
        review.setImageUrls("[]");
        review.setSensitiveHit(false);
        review.setModerationStatus(reviewModerationStatus);
        review.setCreatedAt(LocalDateTime.now());
        if (!"NORMAL".equalsIgnoreCase(reviewModerationStatus)) {
            review.setModeratedAt(LocalDateTime.now());
        }
        resaleReviewRepository.save(review);

        ResaleReviewReportEntity report = new ResaleReviewReportEntity();
        report.setReview(review);
        report.setReporterUser(bob);
        report.setReason("批量处理测试举报");
        report.setStatus(reportStatus);
        report.setCreatedAt(LocalDateTime.now());
        if (processed) {
            report.setProcessedBy("fixture");
            report.setProcessNote("fixture-processed");
            report.setProcessedAt(LocalDateTime.now());
        }
        resaleReviewReportRepository.save(report);
        return report.getId();
    }
}
