package ru.invest.api.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

import static ru.invest.api.common.constants.CacheConstants.BOND_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.BOND_CACHE_NAME;
import static ru.invest.api.common.constants.CacheConstants.COUPON_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.COUPON_CACHE_NAME;

@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    @Primary
    public CacheManager defaultCacheManager() {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheSpecification("maximumSize=10,expireAfterWrite=1m");
        return cacheManager;
    }

    @Bean(BOND_CACHE_MANAGER)
    public CacheManager usersCacheManager() {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager(BOND_CACHE_NAME);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(3000)
                .expireAfterWrite(24, TimeUnit.HOURS));
        return cacheManager;
    }

    @Bean(COUPON_CACHE_MANAGER)
    public CacheManager productsCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(COUPON_CACHE_NAME);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(3000)
                .expireAfterWrite(24, TimeUnit.HOURS));
        return cacheManager;
    }
}
