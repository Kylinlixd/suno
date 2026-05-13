package com.suno.mall.service;

import com.suno.mall.provider.ImageAuditProvider;
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
