package com.suno.mall.service;

import com.suno.mall.common.BizException;
import com.suno.mall.common.ErrorCode;
import com.suno.mall.config.AuthTransactional;
import com.suno.mall.entity.UserAccountEntity;
import com.suno.mall.entity.UserAddressEntity;
import com.suno.mall.dao.UserAddressRepository;
import com.suno.mall.dao.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * M2 收货地址管理。地址始终归属当前用户（JWT 解析），读写均校验所有权。
 */
@Service
public class UserAddressService {

    private final UserAddressRepository userAddressRepository;
    private final UserAccountRepository userAccountRepository;

    public UserAddressService(UserAddressRepository userAddressRepository, UserAccountRepository userAccountRepository) {
        this.userAddressRepository = userAddressRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public List<UserAddressEntity> listByUser(Long userId) {
        return userAddressRepository.findByUser_IdOrderByIsDefaultDescIdDesc(userId);
    }

    @AuthTransactional
    public UserAddressEntity create(Long userId, String receiverName, String receiverPhone,
                                     String province, String city, String district, String detailAddress, boolean isDefault) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在: " + userId, ErrorCode.PARAM_INVALID));
        UserAddressEntity address = new UserAddressEntity();
        applyFields(address, receiverName, receiverPhone, province, city, district, detailAddress);
        address.setUser(user);
        address.setIsDefault(false);
        userAddressRepository.save(address);
        if (isDefault) {
            setDefault(userId, address.getId());
        }
        return address;
    }

    @AuthTransactional
    public UserAddressEntity update(Long userId, Long addressId, String receiverName, String receiverPhone,
                                     String province, String city, String district, String detailAddress) {
        UserAddressEntity address = requireOwned(userId, addressId);
        applyFields(address, receiverName, receiverPhone, province, city, district, detailAddress);
        return userAddressRepository.save(address);
    }

    @AuthTransactional
    public void delete(Long userId, Long addressId) {
        UserAddressEntity address = requireOwned(userId, addressId);
        userAddressRepository.delete(address);
    }

    /** 设为默认地址：清掉旧默认，保证一人一默认 */
    @AuthTransactional
    public UserAddressEntity setDefault(Long userId, Long addressId) {
        UserAddressEntity address = requireOwned(userId, addressId);
        userAddressRepository.findByUser_IdAndIsDefaultTrue(userId)
                .forEach(existing -> existing.setIsDefault(false));
        address.setIsDefault(true);
        return userAddressRepository.save(address);
    }

    private UserAddressEntity requireOwned(Long userId, Long addressId) {
        return userAddressRepository.findByIdAndUser_Id(addressId, userId)
                .orElseThrow(() -> new BizException("收货地址不存在", ErrorCode.ADDRESS_NOT_FOUND));
    }

    private static void applyFields(UserAddressEntity address, String receiverName, String receiverPhone,
                                    String province, String city, String district, String detailAddress) {
        if (isBlank(receiverName) || isBlank(receiverPhone) || isBlank(province)
                || isBlank(city) || isBlank(district) || isBlank(detailAddress)) {
            throw new BizException("收货人、电话与地址信息均不能为空", ErrorCode.PARAM_INVALID);
        }
        if (!receiverPhone.matches("\\+?\\d{5,20}")) {
            throw new BizException("收货电话格式不正确", ErrorCode.PARAM_INVALID);
        }
        address.setReceiverName(receiverName.trim());
        address.setReceiverPhone(receiverPhone.trim());
        address.setProvince(province.trim());
        address.setCity(city.trim());
        address.setDistrict(district.trim());
        address.setDetailAddress(detailAddress.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
