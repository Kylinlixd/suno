package com.suno.mall.dao;

import com.suno.mall.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<UserAccountEntity> {
    Optional<UserAccountEntity> findByUsername(String username);

    boolean existsByUsername(String username);
}
