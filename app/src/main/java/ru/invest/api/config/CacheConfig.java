package ru.invest.api.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

import static ru.invest.api.common.constants.CacheConstants.BOND_REPOSITORY_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.BOND_REPOSITORY_CACHE_NAME;
import static ru.invest.api.common.constants.CacheConstants.BOND_TINKOFF_API_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.BOND_TINKOFF_API_CACHE_NAME;
import static ru.invest.api.common.constants.CacheConstants.BUDGET_ORG_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.BUDGET_ORG_CACHE_NAME;
import static ru.invest.api.common.constants.CacheConstants.CB_RF_ALL_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.CB_RF_ALL_CACHE_NAME;
import static ru.invest.api.common.constants.CacheConstants.COUPON_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.COUPON_CACHE_NAME;
import static ru.invest.api.common.constants.CacheConstants.CURRENCY_PROVIDER_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.CURRENCY_PROVIDER_CACHE_NAME;
import static ru.invest.api.common.constants.CacheConstants.CURRENCY_RATE_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.CURRENCY_RATE_CACHE_NAME;
import static ru.invest.api.common.constants.CacheConstants.MARKET_OVERVIEW_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.MARKET_OVERVIEW_CACHE_NAME;
import static ru.invest.api.common.constants.CacheConstants.OFZ_CURVE_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.OFZ_CURVE_CACHE_NAME;
import static ru.invest.api.common.constants.CacheConstants.PRICE_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.PRICE_CACHE_NAME;

@Configuration
@EnableCaching
@SuppressWarnings("checkstyle:MagicNumber")
public class CacheConfig {
    @Bean
    @Primary
    public CacheManager defaultCacheManager() {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheSpecification("maximumSize=10,expireAfterWrite=1m");
        return cacheManager;
    }

    @Bean(BOND_REPOSITORY_CACHE_MANAGER)
    public CacheManager bondRepositoryCacheManager() {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager(BOND_REPOSITORY_CACHE_NAME);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(3000)
                .expireAfterWrite(24, TimeUnit.HOURS));
        return cacheManager;
    }

    @Bean(BOND_TINKOFF_API_CACHE_MANAGER)
    public CacheManager bondTinkoffApiCacheManager() {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager(BOND_TINKOFF_API_CACHE_NAME);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(3000)
                .expireAfterWrite(24, TimeUnit.HOURS));
        return cacheManager;
    }

    @Bean(PRICE_CACHE_MANAGER)
    public CacheManager priceCacheManager() {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager(PRICE_CACHE_NAME);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(3000)
                .expireAfterWrite(24, TimeUnit.HOURS));
        return cacheManager;
    }

    @Bean(COUPON_CACHE_MANAGER)
    public CacheManager couponsCacheManager() {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager(COUPON_CACHE_NAME);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(3000)
                .expireAfterWrite(24, TimeUnit.HOURS));
        return cacheManager;
    }

    /**
     * Курсы ЦБ на дату перед таблицей currency_rate: курс на прошедшую дату не меняется, кэш только экономит запрос
     * в БД. Пустой результат (ЦБ недоступен, валюты нет) не кэшируется.
     */
    @Bean(CURRENCY_RATE_CACHE_MANAGER)
    public CacheManager currencyRateCacheManager() {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager(CURRENCY_RATE_CACHE_NAME);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(24, TimeUnit.HOURS));
        return cacheManager;
    }

    @Bean(CB_RF_ALL_CACHE_MANAGER)
    public CacheManager cbRfAllCacheManager() {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager(CB_RF_ALL_CACHE_NAME);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(3000)
                .expireAfterWrite(12, TimeUnit.HOURS));
        return cacheManager;
    }

    @Bean(BUDGET_ORG_CACHE_MANAGER)
    public CacheManager budgetOrgCacheManager() {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager(BUDGET_ORG_CACHE_NAME);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(3000)
                .expireAfterWrite(12, TimeUnit.HOURS));
        return cacheManager;
    }

    @Bean(CURRENCY_PROVIDER_CACHE_MANAGER)
    public CacheManager currencyProviderCacheManager() {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager(CURRENCY_PROVIDER_CACHE_NAME);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(3000)
                .expireAfterWrite(12, TimeUnit.HOURS));
        return cacheManager;
    }

    /**
     * Ключевая ставка и курсы для шапки ленты: курс ЦБ меняется раз в день, ставка - несколько раз в год.
     */
    @Bean(MARKET_OVERVIEW_CACHE_MANAGER)
    public CacheManager marketOverviewCacheManager() {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager(MARKET_OVERVIEW_CACHE_NAME);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(30, TimeUnit.MINUTES));
        return cacheManager;
    }

    /**
     * Кривая доходности ОФЗ для спреда: биржа пересчитывает её в течение дня, спред по свежести не точнее цены облигаций.
     * Пустой результат (биржа не ответила) не кэшируется.
     */
    @Bean(OFZ_CURVE_CACHE_MANAGER)
    public CacheManager ofzCurveCacheManager() {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager(OFZ_CURVE_CACHE_NAME);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(1, TimeUnit.HOURS));
        return cacheManager;
    }
}
