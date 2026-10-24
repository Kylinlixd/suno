package com.suno.mall.service;

import com.suno.mall.entity.UserAccountEntity;
import org.springframework.stereotype.Service;

/**
 * P2 会员等级成长：按累计积分阈值升级（NORMAL < 1000 ≤ SILVER < 5000 ≤ GOLD），
 * 只升不降（降级会影响已发权益的公平性）；升级推送站内通知。
 * 积分变动路径（回收入账/下单抵扣/回退）在写流水后调用 {@link #recalculate}。
 */
@Service
public class MemberLevelService {

    public static final String LEVEL_NORMAL = "NORMAL";
    public static final String LEVEL_SILVER = "SILVER";
    public static final String LEVEL_GOLD = "GOLD";
    private static final int SILVER_THRESHOLD = 1000;
    private static final int GOLD_THRESHOLD = 5000;

    private final NotificationService notificationService;

    public MemberLevelService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 返回是否发生了升级 */
    public boolean recalculate(UserAccountEntity user) {
        String target = levelFor(user.getPoints() == null ? 0 : user.getPoints());
        String current = user.getLevel() == null ? LEVEL_NORMAL : user.getLevel();
        if (rank(target) <= rank(current)) {
            return false;
        }
        user.setLevel(target);
        notificationService.push(user.getId(), "MEMBER_LEVEL_UP", "会员等级提升",
                "你的循环等级已升至 " + label(target) + "，感谢每一次让物品继续有用的选择。",
                user.getId(), "USER_ACCOUNT");
        return true;
    }

    public static String levelFor(int points) {
        if (points >= GOLD_THRESHOLD) {
            return LEVEL_GOLD;
        }
        if (points >= SILVER_THRESHOLD) {
            return LEVEL_SILVER;
        }
        return LEVEL_NORMAL;
    }

    private static int rank(String level) {
        return switch (level) {
            case LEVEL_GOLD -> 2;
            case LEVEL_SILVER -> 1;
            default -> 0;
        };
    }

    static String label(String level) {
        return switch (level) {
            case LEVEL_GOLD -> "黄金循环官";
            case LEVEL_SILVER -> "白银循环家";
            default -> "循环新手";
        };
    }
}
