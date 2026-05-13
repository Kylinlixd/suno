package com.suno.mall.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "provider.image-audit", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockImageAuditProvider implements ImageAuditProvider {

    @Override
    public boolean pass(String imageUrl) {
        return imageUrl != null && !imageUrl.isBlank();
    }
}
