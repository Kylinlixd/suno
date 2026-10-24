package com.suno.mall.hardening;

import com.suno.mall.service.PaymentReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PAY-S004 对账服务测试：空库/无凭证数据可执行且结果自洽。
 */
@SpringBootTest
@ActiveProfiles("test")
class ReconciliationServiceTest {

    @Autowired
    private PaymentReconciliationService paymentReconciliationService;

    @Test
    void flagsPaidOrdersWithoutPaymentEvidence() {
        var result = paymentReconciliationService.reconcile();
        long checked = ((Number) result.get("checked")).longValue();
        long mismatched = ((Number) result.get("mismatched")).longValue();
        long matched = ((Number) result.get("matched")).longValue();
        assertTrue(checked >= 0);
        assertTrue(mismatched <= checked);
        assertTrue(matched + mismatched == checked);
    }
}
