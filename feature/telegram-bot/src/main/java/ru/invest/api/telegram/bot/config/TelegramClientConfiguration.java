package ru.invest.api.telegram.bot.config;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

/**
 * Один OkHttp-клиент на long polling и отправку сообщений: прокси из настроек есть только у него, остальные
 * клиенты приложения (T-Invest, ЦБ, Мосбиржа) ходят напрямую.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "ru.invest.api.telegram", name = "enabled", havingValue = "true")
public class TelegramClientConfiguration {
    // getUpdates держит соединение до 50 секунд (timeout по умолчанию), чтение должно ждать дольше
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(75);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    @Bean
    public OkHttpClient telegramOkHttpClient(final TelegramProperties properties) {
        final OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT)
                .readTimeout(READ_TIMEOUT)
                .writeTimeout(CONNECT_TIMEOUT);
        final TelegramProperties.Proxy proxy = properties.getProxy();
        if (proxy.getType() != TelegramProperties.ProxyType.NONE) {
            final Proxy.Type type = proxy.getType() == TelegramProperties.ProxyType.HTTP
                    ? Proxy.Type.HTTP
                    : Proxy.Type.SOCKS;
            // createUnresolved: адрес Telegram разрешает прокси, местный DNS для заблокированного домена может врать
            builder.proxy(new Proxy(type, InetSocketAddress.createUnresolved(proxy.getHost(), proxy.getPort())));
            log.info("Telegram client uses {} proxy {}:{}", proxy.getType(), proxy.getHost(), proxy.getPort());
        }
        return builder.build();
    }

    @Bean
    public TelegramClient telegramClient(final OkHttpClient telegramOkHttpClient, final TelegramProperties properties) {
        return new OkHttpTelegramClient(telegramOkHttpClient, properties.getBotToken());
    }
}
