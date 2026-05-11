package com.recycle.mall.domain.service;

import com.recycle.mall.infrastructure.provider.audit.ImageAuditProvider;
import org.springframework.stereotype.Service;

@Service
public class AiAuditService {

    private final ImageAuditProvider imageAuditProvider;

    public AiAuditService(ImageAuditProvider imageAuditProvider) {
        this.imageAuditProvider = imageAuditProvider;
    }

    public boolean passImageAudit(String imageUrl) {
        return imageAuditProvider.pass(imageUrl);
    }
}
