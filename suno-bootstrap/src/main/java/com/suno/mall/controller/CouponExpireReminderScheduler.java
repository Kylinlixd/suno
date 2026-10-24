package com.suno.mall.controller;

import com.suno.mall.entity.CouponUserEntity;
import com.suno.mall.dao.CouponUserRepository;
import com.suno.mall.service.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MKT-S003 优惠券到期提醒：扫描 3 天内到期、未用且未提醒的券，推送站内通知并落 reminded 标记。
 */
@Component
public class CouponExpireReminderScheduler {

    private final CouponUserRepository couponUserRepository;
    private final NotificationService notificationService;

    public CouponExpireReminderScheduler(CouponUserRepository couponUserRepository,
                                         NotificationService notificationService) {
        this.couponUserRepository = couponUserRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${mall.coupon.expire-reminder-fixed-delay-ms:3600000}")
    @Transactional
    public int remindExpiringCoupons() {
        LocalDateTime now = LocalDateTime.now();
        List<CouponUserEntity> expiring = couponUserRepository.findExpiringUnreminded(now, now.plusDays(3));
        for (CouponUserEntity mine : expiring) {
            notificationService.push(mine.getUser().getId(), "COUPON_EXPIRING", "优惠券即将到期",
                    "你的券「" + mine.getCoupon().getName() + "」将于 " + mine.getExpireAt().toLocalDate() + " 到期，尽快使用。",
                    mine.getId(), "COUPON_USER");
            mine.setExpireRemindedAt(now);
            couponUserRepository.save(mine);
        }
        return expiring.size();
    }
}
