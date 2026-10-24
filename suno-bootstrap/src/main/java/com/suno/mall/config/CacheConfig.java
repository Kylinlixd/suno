
package com.suno.mall.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 缓存配置。
 * <p>
 * Redis 是可选依赖（见 README「Cache」与 application-redis.yml）：仅当 {@code redis}
 * profile 激活时使用 RedisCacheManager（带分缓存 TTL）；其余环境（默认 H2 dev/test）
 * 回落到进程内缓存，保证无 Redis 时审核/发布等 @CacheEvict 流程可用。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    @Bean
    @Profile("redis")
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 默认缓存配置
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) // 默认10分钟过期
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // 为不同的缓存设置不同的过期时间
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 订单状态缓存5分钟
        cacheConfigurations.put("orderStatus", defaultCacheConfig.entryTtl(Duration.ofMinutes(5)));

        // 商品信息缓存10分钟
        cacheConfigurations.put("productInfo", defaultCacheConfig.entryTtl(Duration.ofMinutes(10)));

        // 用户信息缓存15分钟
        cacheConfigurations.put("userInfo", defaultCacheConfig.entryTtl(Duration.ofMinutes(15)));

        // 二销上架列表缓存2分钟
        cacheConfigurations.put("resaleListing", defaultCacheConfig.entryTtl(Duration.ofMinutes(2)));

        // 回收订单状态缓存5分钟
        cacheConfigurations.put("recycleOrder", defaultCacheConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    /**
     * 无 Redis 环境的进程内兜底缓存：无 TTL，仅用于本地开发与测试，
     * 生产部署应激活 {@code redis} profile 获得过期与跨实例共享能力。
     */
    @Bean
    @Profile("!redis")
    public CacheManager inMemoryCacheManager() {
        return new ConcurrentMapCacheManager();
    }
}
