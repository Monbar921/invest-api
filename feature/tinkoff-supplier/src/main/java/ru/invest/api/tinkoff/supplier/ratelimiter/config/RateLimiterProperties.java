package ru.invest.api.tinkoff.supplier.ratelimiter.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Клиентские лимиты запросов к Tinkoff Invest API.
 * <p>
 * Лимиты сервера (developer.tbank.ru/invest/intro/intro/limits): InstrumentsService - 200 запросов в минуту,
 * метод Bonds отдельно - 15 в минуту, MarketDataService - 600 в минуту. Значения по умолчанию взяты с запасом
 * и раздаются маленькими порциями каждую секунду, чтобы не было всплеска в начале минуты.
 * <p>
 * Rate limiter в SDK создаётся на каждый gRPC-метод отдельно, а лимит сервера - на весь сервис.
 * При добавлении новых методов InstrumentsService суммарный лимит нужно пересмотреть.
 */
@Data
@Component
@ConfigurationProperties(prefix = "invest.rate-limiter")
@SuppressWarnings("checkstyle:MagicNumber")
public class RateLimiterProperties {
    /**
     * InstrumentsService/GetBondCoupons: 3 в секунду = 180 в минуту (лимит сервиса - 200)
     */
    private Limit bondCoupons = new Limit(3, Duration.ofSeconds(1));

    /**
     * InstrumentsService/Bonds: 1 раз в 5 секунд = 12 в минуту (лимит метода - 15)
     */
    private Limit bonds = new Limit(1, Duration.ofSeconds(5));

    /**
     * MarketDataService/GetLastPrices: 9 в секунду = 540 в минуту (лимит сервиса - 600)
     */
    private Limit lastPrices = new Limit(9, Duration.ofSeconds(1));

    /**
     * Лимит для остальных методов, для которых нет отдельной настройки
     */
    private Limit defaultLimit = new Limit(1, Duration.ofSeconds(1));

    /**
     * Сколько вызов ждёт свободного разрешения (и свободного слота bulkhead), прежде чем упасть
     */
    private Duration timeout = Duration.ofSeconds(60);

    /**
     * Максимум одновременных вызовов одного метода
     */
    private int maxConcurrentCalls = 25;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Limit {
        /**
         * Сколько запросов разрешено за период
         */
        private int requests;

        /**
         * Период, после которого разрешения восстанавливаются
         */
        private Duration period;
    }
}
