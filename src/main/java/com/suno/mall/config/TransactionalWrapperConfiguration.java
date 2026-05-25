package com.suno.mall.config;

import com.suno.mall.service.support.AuditContext;
import com.suno.mall.service.support.VersionHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Transactional wrapper configuration.
 */
@Configuration
public class TransactionalWrapperConfiguration {

    @Bean
    public VersionHelper versionHelper() {
        return new VersionHelper();
    }

    @Bean
    public AuditContext auditContext() {
        return AuditContext.empty();
    }
}
