package com.recycle.mall.infrastructure.repository;

import com.recycle.mall.infrastructure.entity.AuthTokenBlacklistEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AuthTokenBlacklistRepository extends JpaRepository<AuthTokenBlacklistEntity, Long> {
    boolean existsByJti(String jti);

    void deleteByExpireAtBefore(LocalDateTime time);
}
