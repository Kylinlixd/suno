package com.recycle.mall.service;

import com.recycle.mall.entity.GradeLevel;
import com.recycle.mall.entity.ProductDraft;
import com.recycle.mall.entity.ValuationResult;
import com.recycle.mall.entity.ValuationRuleEntity;
import com.recycle.mall.dao.ValuationRuleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
public class ValuationService {

    private final ValuationRuleRepository valuationRuleRepository;

    public ValuationService(ValuationRuleRepository valuationRuleRepository) {
        this.valuationRuleRepository = valuationRuleRepository;
    }

    public ValuationResult evaluate(ProductDraft draft) {
        long months = ChronoUnit.MONTHS.between(draft.productionDate(), LocalDate.now());
        List<ValuationRuleEntity> rules = valuationRuleRepository.findByBrandInAndModelIn(
                List.of(draft.brand(), "ALL"),
                List.of(draft.model(), "ALL")
        );
        return rules.stream()
                .filter(rule -> months >= rule.getMinMonths() && months <= rule.getMaxMonths())
                .filter(rule -> draft.wearScore() >= rule.getMinWearScore() && draft.wearScore() <= rule.getMaxWearScore())
                .min(Comparator.comparingInt(this::rulePriority))
                .map(rule -> new ValuationResult(GradeLevel.valueOf(rule.getGrade()), rule.getPrice()))
                .orElseGet(() -> fallbackByMonths(months));
    }

    private int rulePriority(ValuationRuleEntity rule) {
        int brandPriority = "ALL".equals(rule.getBrand()) ? 1 : 0;
        int modelPriority = "ALL".equals(rule.getModel()) ? 1 : 0;
        return brandPriority + modelPriority;
    }

    private ValuationResult fallbackByMonths(long months) {
        GradeLevel level;
        BigDecimal price;

        if (months <= 18) {
            level = GradeLevel.GOOD;
            price = new BigDecimal("1800");
        } else if (months <= 36) {
            level = GradeLevel.MEDIUM;
            price = new BigDecimal("1200");
        } else {
            level = GradeLevel.UNQUALIFIED;
            price = new BigDecimal("300");
        }
        return new ValuationResult(level, price);
    }
}
