package com.suno.mall.dao;

import com.recycle.mall.entity.AuthRefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshTokenEntity, Long> {
    Optional<AuthRefreshTokenEntity> findByToken(String token);

    void deleteByExpireAtBefore(LocalDateTime time);

    List<AuthRefreshTokenEntity> findByUsernameAndRevokedFalse(String username);

    List<AuthRefreshTokenEntity> findByUsernameAndRevokedFalseOrderByCreatedAtDesc(String username);

    @Query("select t from AuthRefreshTokenEntity t where t.username = :username and t.deviceId = :deviceId and t.revoked = false")
    List<AuthRefreshTokenEntity> findActiveByUsernameAndDeviceId(
            @Param("username") String username,
            @Param("deviceId") String deviceId
    );
}
