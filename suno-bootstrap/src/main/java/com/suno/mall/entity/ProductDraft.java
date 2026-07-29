package com.suno.mall.entity;

import java.time.LocalDate;

public record ProductDraft(
        String snCode,
        String brand,
        String model,
        LocalDate productionDate,
        String imageUrl,
        int wearScore
) {
}
