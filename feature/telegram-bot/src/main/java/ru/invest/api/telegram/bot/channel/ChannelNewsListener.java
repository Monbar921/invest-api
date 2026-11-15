package ru.invest.api.telegram.bot.channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.invest.api.common.model.event.NewsSyncedEvent;

import java.time.Instant;

/**
 * Лента новостей загружена (раз в 15 минут) - собрать посты канала. Публикует их шедулер: с паузой и лимитом в день.
 * Ошибка сбора не должна ломать загрузку новостей: она пишется в лог.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.telegram", name = "enabled", havingValue = "true")
public class ChannelNewsListener {
    private final ChannelProperties properties;
    private final ChannelUseCase channelUseCase;

    @EventListener
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onNewsSynced(final NewsSyncedEvent event) {
        if (!properties.isEnabled() || !properties.isCollectOnSync()) {
            return;
        }
        try {
            channelUseCase.collectNews(Instant.now());
        } catch (final RuntimeException e) {
            log.error("Telegram channel: collecting posts after news sync failed", e);
        }
    }
}
