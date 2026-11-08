package ru.invest.api.telegram.bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Telegram-бот. Токен бота - только из переменной окружения, в конфиг и репозиторий не попадает.
 */
@Data
@Component
@SuppressWarnings("checkstyle:MagicNumber")
@ConfigurationProperties(prefix = "ru.invest.api.telegram")
public class TelegramProperties {
    /**
     * false - бот выключен: нет клиента Telegram, long polling и проверки связи; привязка недоступна.
     */
    private boolean enabled;

    /**
     * false - бот включён, но обновления не запрашиваются и связь не проверяется (тесты вызывают обработчик сами).
     */
    private boolean polling = true;

    private String botToken;

    /**
     * Имя бота без @ - для ссылки привязки https://t.me/&lt;bot&gt;?start=&lt;токен&gt;.
     */
    private String botUsername;

    /**
     * Сколько живёт токен ссылки привязки.
     */
    private Duration linkTokenTtl = Duration.ofMinutes(10);

    /**
     * Как часто проверять, что Telegram отвечает (getMe) - для статуса в UI.
     */
    private Duration availabilityCheckInterval = Duration.ofMinutes(5);

    private Proxy proxy = new Proxy();

    @Data
    public static class Proxy {
        /**
         * NONE - напрямую (или через TUN VPN); HTTP - CONNECT, имя хоста разрешается на прокси; SOCKS - SOCKS5.
         */
        private ProxyType type = ProxyType.NONE;
        private String host;
        private Integer port;
    }

    public enum ProxyType {
        NONE,
        HTTP,
        SOCKS
    }
}
