package com.suno.mall.controller;

import com.suno.mall.dao.SmsCodeRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 短信验证码保留期清理：每小时删除过期验证码（含已用/未用），
 * 防止 suno_sms_code 无界增长。
 */
@Component
public class SmsCodeCleanupScheduler {

    private final SmsCodeRepository smsCodeRepository;

    public SmsCodeCleanupScheduler(SmsCodeRepository smsCodeRepository) {
        this.smsCodeRepository = smsCodeRepository;
    }

    @Scheduled(fixedDelayString = "${security.auth.sms.cleanup-fixed-delay-ms:3600000}")
    @Transactional
    public long cleanupExpiredCodes() {
        return smsCodeRepository.deleteByExpireAtBefore(LocalDateTime.now());
    }
}
