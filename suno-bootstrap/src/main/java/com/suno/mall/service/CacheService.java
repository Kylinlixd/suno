
package com.suno.mall.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 缓存服务
 * 提供统一的缓存操作接口，支持缓存读写和过期控制
 */
@Service
public class CacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 设置缓存
     * @param key 缓存键
     * @param value 缓存值
     * @param timeout 过期时间(秒)
     */
    public void set(String key, Object value, long timeout) {
        redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);
    }

    /**
     * 获取缓存
     * @param key 缓存键
     * @return 缓存值
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 删除缓存
     * @param key 缓存键
     */
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 检查缓存是否存在
     * @param key 缓存键
     * @return 是否存在
     */
    public boolean exists(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 设置缓存过期时间
     * @param key 缓存键
     * @param timeout 过期时间(秒)
     */
    public void expire(String key, long timeout) {
        redisTemplate.expire(key, timeout, TimeUnit.SECONDS);
    }

    /**
     * 获取缓存剩余过期时间(秒)
     * @param key 缓存键
     * @return 剩余过期时间(秒)
     */
    public long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 使用注解缓存订单状态
     * @param orderNo 订单号
     * @param status 订单状态
     * @return 订单状态
     */
    @CachePut(value = "orderStatus", key = "#orderNo")
    public String cacheOrderStatus(String orderNo, String status) {
        return status;
    }

    /**
     * 使用注解获取订单状态缓存
     * @param orderNo 订单号
     * @return 订单状态
     */
    @Cacheable(value = "orderStatus", key = "#orderNo")
    public String getOrderStatus(String orderNo) {
        return null;
    }

    /**
     * 使用注解删除订单状态缓存
     * @param orderNo 订单号
     */
    @CacheEvict(value = "orderStatus", key = "#orderNo")
    public void evictOrderStatus(String orderNo) {
        // 方法体为空，仅用于触发缓存删除
    }

    /**
     * 使用注解缓存商品信息
     * @param productId 商品ID
     * @param product 商品信息
     * @return 商品信息
     */
    @CachePut(value = "productInfo", key = "#productId")
    public Object cacheProductInfo(Long productId, Object product) {
        return product;
    }

    /**
     * 使用注解获取商品信息缓存
     * @param productId 商品ID
     * @return 商品信息
     */
    @Cacheable(value = "productInfo", key = "#productId")
    public Object getProductInfo(Long productId) {
        return null;
    }

    /**
     * 使用注解删除商品信息缓存
     * @param productId 商品ID
     */
    @CacheEvict(value = "productInfo", key = "#productId")
    public void evictProductInfo(Long productId) {
        // 方法体为空，仅用于触发缓存删除
    }

    /**
     * 使用注解缓存用户信息
     * @param userId 用户ID
     * @param user 用户信息
     * @return 用户信息
     */
    @CachePut(value = "userInfo", key = "#userId")
    public Object cacheUserInfo(Long userId, Object user) {
        return user;
    }

    /**
     * 使用注解获取用户信息缓存
     * @param userId 用户ID
     * @return 用户信息
     */
    @Cacheable(value = "userInfo", key = "#userId")
    public Object getUserInfo(Long userId) {
        return null;
    }

    /**
     * 使用注解删除用户信息缓存
     * @param userId 用户ID
     */
    @CacheEvict(value = "userInfo", key = "#userId")
    public void evictUserInfo(Long userId) {
        // 方法体为空，仅用于触发缓存删除
    }

    /**
     * 使用注解缓存二销上架列表
     * @param listingId 上架ID
     * @param listing 上架信息
     * @return 上架信息
     */
    @CachePut(value = "resaleListing", key = "#listingId")
    public Object cacheResaleListing(Long listingId, Object listing) {
        return listing;
    }

    /**
     * 使用注解获取二销上架列表缓存
     * @param listingId 上架ID
     * @return 上架信息
     */
    @Cacheable(value = "resaleListing", key = "#listingId")
    public Object getResaleListing(Long listingId) {
        return null;
    }

    /**
     * 使用注解删除二销上架列表缓存
     * @param listingId 上架ID
     */
    @CacheEvict(value = "resaleListing", key = "#listingId")
    public void evictResaleListing(Long listingId) {
        // 方法体为空，仅用于触发缓存删除
    }

    /**
     * 使用注解缓存回收订单状态
     * @param orderNo 订单号
     * @param status 订单状态
     * @return 订单状态
     */
    @CachePut(value = "recycleOrder", key = "#orderNo")
    public String cacheRecycleOrderStatus(String orderNo, String status) {
        return status;
    }

    /**
     * 使用注解获取回收订单状态缓存
     * @param orderNo 订单号
     * @return 订单状态
     */
    @Cacheable(value = "recycleOrder", key = "#orderNo")
    public String getRecycleOrderStatus(String orderNo) {
        return null;
    }

    /**
     * 使用注解删除回收订单状态缓存
     * @param orderNo 订单号
     */
    @CacheEvict(value = "recycleOrder", key = "#orderNo")
    public void evictRecycleOrderStatus(String orderNo) {
        // 方法体为空，仅用于触发缓存删除
    }
}
