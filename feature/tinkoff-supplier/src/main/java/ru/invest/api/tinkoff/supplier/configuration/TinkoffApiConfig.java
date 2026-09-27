package ru.invest.api.tinkoff.supplier.configuration;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.RetryConfig;
import io.grpc.Status;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.invest.api.tinkoff.supplier.ratelimiter.config.RateLimiterProperties;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;
import ru.tinkoff.piapi.contract.v1.MarketDataServiceGrpc;
import ru.ttech.piapi.core.connector.ConnectorConfiguration;
import ru.ttech.piapi.core.connector.ServiceStubFactory;
import ru.ttech.piapi.core.connector.exception.ServiceRuntimeException;
import ru.ttech.piapi.core.connector.resilience.ResilienceConfiguration;
import ru.ttech.piapi.core.connector.resilience.ResilienceSyncStubWrapper;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Клиенты Tinkoff Invest API с защитой из SDK (resilience4j): rate limiter, bulkhead и retry на каждый метод.
 * Retry повторяет только RESOURCE_EXHAUSTED и ждёт до сброса лимита (заголовок x-ratelimit-reset),
 * число попыток и пауза по умолчанию задаются в invest.connector.connection.retry.*
 */
@Configuration
@Slf4j
@ConditionalOnProperty(
        prefix = "invest",
        name = "enabled",
        havingValue = "true"
)
public class TinkoffApiConfig {
    // нужен SDK для resilience-обёрток; не бин, чтобы не конфликтовать с другими ScheduledExecutorService
    private final ScheduledExecutorService resilienceExecutorService = Executors.newSingleThreadScheduledExecutor();

    @Bean
    public ResilienceConfiguration tinkoffResilienceConfiguration(final ServiceStubFactory serviceStubFactory,
                                                                  final RateLimiterProperties properties) {
        final ConnectorConfiguration connectorConfiguration = serviceStubFactory.getConfiguration();

        return ResilienceConfiguration.builder(resilienceExecutorService, connectorConfiguration)
                .withDefaultRetry(toRetryConfig(connectorConfiguration))
                .withDefaultRateLimiter(toRateLimiterConfig(properties.getDefaultLimit(), properties))
                .withRateLimiterForMethod(InstrumentsServiceGrpc.getGetBondCouponsMethod(),
                        toRateLimiterConfig(properties.getBondCoupons(), properties))
                .withRateLimiterForMethod(InstrumentsServiceGrpc.getBondsMethod(),
                        toRateLimiterConfig(properties.getBonds(), properties))
                .withRateLimiterForMethod(MarketDataServiceGrpc.getGetLastPricesMethod(),
                        toRateLimiterConfig(properties.getLastPrices(), properties))
                .withDefaultBulkHead(BulkheadConfig.custom()
                        .maxConcurrentCalls(properties.getMaxConcurrentCalls())
                        .maxWaitDuration(properties.getTimeout())
                        .build())
                .build();
    }

    @Bean
    public ResilienceSyncStubWrapper<InstrumentsServiceGrpc.InstrumentsServiceBlockingStub> instrumentsService(
            final ServiceStubFactory serviceStubFactory, final ResilienceConfiguration tinkoffResilienceConfiguration) {
        return serviceStubFactory.newResilienceSyncService(InstrumentsServiceGrpc::newBlockingStub, tinkoffResilienceConfiguration);
    }

    @Bean
    public ResilienceSyncStubWrapper<MarketDataServiceGrpc.MarketDataServiceBlockingStub> marketDataService(
            final ServiceStubFactory serviceStubFactory, final ResilienceConfiguration tinkoffResilienceConfiguration) {
        return serviceStubFactory.newResilienceSyncService(MarketDataServiceGrpc::newBlockingStub, tinkoffResilienceConfiguration);
    }

    @PreDestroy
    public void destroy() {
        resilienceExecutorService.shutdown();
    }

    /**
     * Повторяет логику retry по умолчанию из SDK. Её нельзя использовать как есть: SDK 1.42 собран под resilience4j 1.x
     * (io.vavr.control.Either), а BOM spring-cloud-dependencies подтягивает resilience4j 2.x - на первом же повторе
     * функция интервала из SDK падает с ClassCastException.
     */
    private RetryConfig toRetryConfig(final ConnectorConfiguration connectorConfiguration) {
        final long waitDurationMs = connectorConfiguration.getWaitDuration();

        return RetryConfig.custom()
                .maxAttempts(connectorConfiguration.getMaxAttempts())
                .retryOnException(this::isResourceExhausted)
                .intervalBiFunction((attempt, result) -> {
                    if (result.isLeft() && result.getLeft() instanceof final ServiceRuntimeException e) {
                        final long rateLimitResetMs = e.getRateLimitReset() * 1000L;
                        return rateLimitResetMs > 0 ? rateLimitResetMs : waitDurationMs;
                    }
                    return waitDurationMs;
                })
                .build();
    }

    private boolean isResourceExhausted(final Throwable throwable) {
        return throwable instanceof final ServiceRuntimeException e
                && e.getErrorType() == Status.Code.RESOURCE_EXHAUSTED;
    }

    private RateLimiterConfig toRateLimiterConfig(final RateLimiterProperties.Limit limit, final RateLimiterProperties properties) {
        return RateLimiterConfig.custom()
                .limitForPeriod(limit.getRequests())
                .limitRefreshPeriod(limit.getPeriod())
                .timeoutDuration(properties.getTimeout())
                .build();
    }
}
