package ru.invest.api.tinkoff.supplier.ratelimiter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "invest.rate-limiter")
public class RateLimiterProperties {

    /** Максимальное число запросов в секунду */
    private int requestsPerSecond = 10;

    /** Максимальное число запросов в минуту */
    private int requestsPerMinute = 200;

    /** Таймаут ожидания освобождения лимита (мс) */
    private long timeoutMs = 30_000;
}
