package com.suno.mall.dao;

import com.suno.mall.entity.AuthTokenBlacklistEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AuthTokenBlacklistRepository extends JpaRepository<AuthTokenBlacklistEntity, Long> {
    boolean existsByJti(String jti);

    void deleteByExpireAtBefore(LocalDateTime time);
}
