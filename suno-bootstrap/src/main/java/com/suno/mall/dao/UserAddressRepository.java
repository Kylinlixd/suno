package com.suno.mall.dao;

import com.suno.mall.entity.UserAddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAddressRepository extends JpaRepository<UserAddressEntity, Long> {

    List<UserAddressEntity> findByUser_IdOrderByIsDefaultDescIdDesc(Long userId);

    Optional<UserAddressEntity> findByIdAndUser_Id(Long id, Long userId);

    List<UserAddressEntity> findByUser_IdAndIsDefaultTrue(Long userId);
}
