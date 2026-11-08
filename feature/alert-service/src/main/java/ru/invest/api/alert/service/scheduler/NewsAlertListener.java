package ru.invest.api.alert.service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.invest.api.alert.service.config.AlertProperties;
import ru.invest.api.alert.service.usecase.NewsAlertCheckUseCase;
import ru.invest.api.alert.service.usecase.NotificationDispatchUseCase;
import ru.invest.api.common.model.event.NewsSyncedEvent;

/**
 * Лента новостей загружена (раз в 15 минут) - проверить правила по новостям, рейтингам и ключевой ставке. Ошибка
 * проверки не должна ломать загрузку новостей: она пишется в лог.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsAlertListener {
    private final AlertProperties properties;
    private final NewsAlertCheckUseCase newsAlertCheckUseCase;
    private final NotificationDispatchUseCase dispatchUseCase;

    @EventListener
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onNewsSynced(final NewsSyncedEvent event) {
        if (!properties.isNewsCheckOnSync()) {
            return;
        }
        try {
            if (newsAlertCheckUseCase.checkAll() > 0) {
                dispatchUseCase.dispatchSoon();
            }
        } catch (final RuntimeException e) {
            log.error("News alert check after news sync failed", e);
        }
    }
}
