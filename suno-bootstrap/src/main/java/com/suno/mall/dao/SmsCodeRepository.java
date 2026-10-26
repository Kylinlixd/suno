package com.suno.mall.dao;

import com.suno.mall.entity.SmsCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SmsCodeRepository extends JpaRepository<SmsCodeEntity, Long> {

    List<SmsCodeEntity> findTop3ByPhoneOrderByCreatedAtDesc(String phone);

    /** 验证并消费：仅未使用且未过期的最新一条匹配码会被消费 */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE SmsCodeEntity c
            SET c.used = true
            WHERE c.id = :id AND c.used = false AND c.expireAt > :now
            """)
    int consume(@Param("id") Long id, @Param("now") LocalDateTime now);

    Optional<SmsCodeEntity> findByIdAndPhoneAndCodeAndUsedFalse(Long id, String phone, String code);

    /** 过期验证码批量删除（清理调度器用） */
    long deleteByExpireAtBefore(java.time.LocalDateTime cutoff);
}
