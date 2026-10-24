package com.suno.mall.service;

import com.suno.mall.common.BizException;
import com.suno.mall.common.ErrorCode;
import com.suno.mall.entity.SmsCodeEntity;
import com.suno.mall.entity.UserAccountEntity;
import com.suno.mall.dao.SmsCodeRepository;
import com.suno.mall.dao.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * P1 短信验证码：6 位数字码、5 分钟有效、60 秒发送间隔、验证即消费。
 * 发送通道由日志 Provider 兜底（生产接短信服务商时替换 {@link #send}）。
 */
@Service
public class SmsCodeService {

    private static final Logger log = LoggerFactory.getLogger(SmsCodeService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SmsCodeRepository smsCodeRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthApplicationService authApplicationService;
    private final int resendIntervalSeconds;
    private final int expireMinutes;
    private final boolean devEcho;

    public SmsCodeService(SmsCodeRepository smsCodeRepository,
                          UserAccountRepository userAccountRepository,
                          PasswordEncoder passwordEncoder,
                          AuthApplicationService authApplicationService,
                          @Value("${security.auth.sms.resend-interval-seconds:60}") int resendIntervalSeconds,
                          @Value("${security.auth.sms.expire-minutes:5}") int expireMinutes,
                          @Value("${security.auth.sms.dev-echo:true}") boolean devEcho) {
        this.smsCodeRepository = smsCodeRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.authApplicationService = authApplicationService;
        this.resendIntervalSeconds = resendIntervalSeconds;
        this.expireMinutes = expireMinutes;
        this.devEcho = devEcho;
    }

    @Transactional
    public java.util.Map<String, Object> send(String phone) {
        String normalized = normalizePhone(phone);
        List<SmsCodeEntity> recent = smsCodeRepository.findTop3ByPhoneOrderByCreatedAtDesc(normalized);
        if (!recent.isEmpty()
                && recent.get(0).getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(resendIntervalSeconds))) {
            throw new BizException("发送太频繁，请稍后再试", ErrorCode.PARAM_INVALID);
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        SmsCodeEntity entity = new SmsCodeEntity();
        entity.setPhone(normalized);
        entity.setCode(code);
        entity.setExpireAt(LocalDateTime.now().plusMinutes(expireMinutes));
        entity.setUsed(false);
        entity.setCreatedAt(LocalDateTime.now());
        smsCodeRepository.save(entity);
        // 生产环境替换为短信服务商调用；日志只记脱敏手机号
        log.info("sms code sent to {}****{}", normalized.substring(0, 3), normalized.substring(normalized.length() - 2));
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("phone", normalized);
        result.put("expireMinutes", expireMinutes);
        if (devEcho) {
            // 仅开发/联调回显，生产必须配置 dev-echo=false
            result.put("devCode", code);
        }
        return result;
    }

    /** 验证码校验通过后：已有账户直接签发令牌，否则自动注册（用户名=手机号）。 */
    @Transactional
    public java.util.Map<String, Object> loginBySms(String phone, String code, String deviceId) {
        String normalized = normalizePhone(phone);
        if (code == null || !code.matches("\\d{6}")) {
            throw new BizException("验证码格式不正确", ErrorCode.PARAM_INVALID);
        }
        SmsCodeEntity matched = smsCodeRepository.findTop3ByPhoneOrderByCreatedAtDesc(normalized).stream()
                .filter(candidate -> code.equals(candidate.getCode()) && !Boolean.TRUE.equals(candidate.getUsed()))
                .filter(candidate -> candidate.getExpireAt().isAfter(LocalDateTime.now()))
                .findFirst()
                .orElseThrow(() -> new BizException("验证码错误或已过期", ErrorCode.AUTH_BAD_CREDENTIALS));
        if (smsCodeRepository.consume(matched.getId(), LocalDateTime.now()) == 0) {
            throw new BizException("验证码已被使用", ErrorCode.AUTH_BAD_CREDENTIALS);
        }
        return authApplicationService.loginOrRegisterByPhone(normalized, deviceId);
    }

    static String normalizePhone(String phone) {
        if (phone == null) {
            throw new BizException("手机号不能为空", ErrorCode.PARAM_INVALID);
        }
        String trimmed = phone.trim();
        if (!trimmed.matches("1\\d{10}")) {
            throw new BizException("手机号格式不正确", ErrorCode.PARAM_INVALID);
        }
        return trimmed;
    }
}
