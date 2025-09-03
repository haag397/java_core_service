package com.arch.core_service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(RedisConnectionFactory.class)
@EnableConfigurationProperties(RedisProperties.class)
@ConditionalOnProperty(name = "core.cache.enabled", havingValue = "true", matchIfMissing = true)
@EnableCaching
public class RedisAutoConfiguration {

    private final RedisProperties properties;

    @Bean
    @ConditionalOnMissingBean
    public RedisConnectionFactory redisConnectionFactory() {
        // Server config (standalone, plain TCP)
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(
                properties.getHost(), properties.getPort());
        server.setDatabase(properties.getDatabase());
        if (properties.getUsername() != null) server.setUsername(properties.getUsername());
        if (properties.getPassword() != null) server.setPassword(properties.getPassword());

        // Client config (no SSL)
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(properties.getTimeout())
                .build();

        return new LettuceConnectionFactory(server, client);
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheManager cacheManager(RedisConnectionFactory cf) {
        var keySer = new StringRedisSerializer();
        var valSer = new GenericJackson2JsonRedisSerializer();

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(keySer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valSer))
                .entryTtl(properties.getCache().getTimeToLive());

        if (!properties.getCache().isAllowNullValues()) {
            base = base.disableCachingNullValues();
        }

        if (properties.getCache().isUsePrefix()) {
            String global = properties.getCache().getKeyPrefix();
            base = base.computePrefixWith(name -> global + name + "::");
        } else {
            base = base.disableKeyPrefix();
        }

        final RedisCacheConfiguration finalBase = base; // capture safely

        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        properties.getCache().getCacheExpirations()
                .forEach((cacheName, ttl) -> perCache.put(cacheName, finalBase.entryTtl(ttl)));

        var builder = RedisCacheManager.builder(cf)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(perCache);

        if (properties.getCache().isTransactionAware()) {
            builder = builder.transactionAware();
        }

        return builder.build();
    }

    // Optional templates (no SSL logic here either)
    @Bean
    @ConditionalOnMissingBean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory cf) {
        var tpl = new RedisTemplate<String, Object>();
        tpl.setConnectionFactory(cf);
        tpl.setKeySerializer(new StringRedisSerializer());
        tpl.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        tpl.afterPropertiesSet();
        return tpl;
    }
}