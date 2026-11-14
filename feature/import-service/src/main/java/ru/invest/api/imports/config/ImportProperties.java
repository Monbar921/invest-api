package ru.invest.api.imports.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Base64;

/**
 * Импорт сделок от брокера. Ключ шифрования токенов - только из переменной окружения; без ключа импорт по токену
 * выключен (из файла работает). Неверный ключ не даёт приложению стартовать.
 */
@Data
@Component
@Validated
@SuppressWarnings("checkstyle:MagicNumber")
@ConfigurationProperties(prefix = "ru.invest.api.imports")
public class ImportProperties {
    /** AES-256: 32 байта */
    public static final int KEY_BYTES = 32;

    /** Ключ шифрования токенов брокера: 32 случайных байта в Base64. Пусто - импорт по токену выключен */
    private String tokenEncryptionKey;

    /** Сколько хранится неподтверждённый предпросмотр */
    @NotNull
    private Duration previewTtl = Duration.ofDays(7);

    /** Сделок в одном импорте не больше - защита от огромных отчётов */
    @Min(1)
    private int maxItems = 5000;

    /** Размер файла отчёта, байт */
    @Min(1)
    private int maxFileSize = 5 * 1024 * 1024;

    /** Дата сделки - по этому поясу (как в отчётах брокеров) */
    @NotNull
    private ZoneId zone = ZoneId.of("Europe/Moscow");

    @Valid
    @NotNull
    private Tinvest tinvest = new Tinvest();

    @AssertTrue(message = "token-encryption-key: 32 байта в Base64 (openssl rand -base64 32) или пусто")
    public boolean isTokenEncryptionKeyValid() {
        if (tokenEncryptionKey == null || tokenEncryptionKey.isBlank()) {
            return true;
        }
        try {
            return Base64.getDecoder().decode(tokenEncryptionKey.strip()).length == KEY_BYTES;
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    /** Импорт по токену включён - ключ шифрования задан */
    public boolean isTokenImportEnabled() {
        return tokenEncryptionKey != null && !tokenEncryptionKey.isBlank();
    }

    @Data
    public static class Tinvest {
        /** Адрес T-Invest API (gRPC); песочница - sandbox-invest-public-api.tinkoff.ru:443 */
        @NotBlank
        private String target = "invest-public-api.tinkoff.ru:443";

        /** Сколько ждать ответа на один запрос */
        @NotNull
        private Duration timeout = Duration.ofSeconds(30);

        /** Заголовок x-app-name */
        @NotBlank
        private String appName = "invest-api";

        /** Операций со счёта не больше - дальше история не читается, в отчёте предупреждение */
        @Min(1)
        private int maxOperations = 20000;

        /** Имя брокера у импортированных сделок */
        @NotBlank
        private String brokerName = "Т-Инвестиции";
    }
}
