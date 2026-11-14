package ru.invest.api.alert.service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.invest.api.alert.service.config.AlertProperties;
import ru.invest.api.alert.service.usecase.FilterAlertCheckUseCase;
import ru.invest.api.alert.service.usecase.NotificationDispatchUseCase;
import ru.invest.api.alert.service.usecase.PriceAlertCheckUseCase;
import ru.invest.api.common.model.event.PricesSyncedEvent;

import java.time.LocalDate;

/**
 * Цены обновились (частая синхронизация в торговые часы, ночная цепочка, ручной запуск) - проверить правила по цене и
 * доходности и по сохранённым фильтрам. Ошибка проверки не должна ломать синхронизацию цен: она пишется в лог.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceAlertListener {
    private final AlertProperties properties;
    private final PriceAlertCheckUseCase priceAlertCheckUseCase;
    private final FilterAlertCheckUseCase filterAlertCheckUseCase;
    private final NotificationDispatchUseCase dispatchUseCase;

    @EventListener
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onPricesSynced(final PricesSyncedEvent event) {
        if (!properties.isPriceCheckOnSync()) {
            return;
        }
        int queued = 0;
        try {
            queued += priceAlertCheckUseCase.checkAll(LocalDate.now(properties.getZone()));
        } catch (final RuntimeException e) {
            log.error("Price alert check after price sync failed", e);
        }
        try {
            queued += filterAlertCheckUseCase.checkAll();
        } catch (final RuntimeException e) {
            log.error("Saved filter alert check after price sync failed", e);
        }
        if (queued > 0) {
            dispatchUseCase.dispatchSoon();
        }
    }
}
