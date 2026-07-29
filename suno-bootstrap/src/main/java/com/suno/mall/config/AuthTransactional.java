package com.suno.mall.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Auth module transaction annotation.
 * APPLIED AuthApplicationService / AuthSessionService / related Repositories.
 * REQUIRES_NEW + READ_COMMITTED, prevent deadlock, independent commit.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
        isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED,
        rollbackFor = Exception.class,
        timeout = 30
)
public @interface AuthTransactional {
}
