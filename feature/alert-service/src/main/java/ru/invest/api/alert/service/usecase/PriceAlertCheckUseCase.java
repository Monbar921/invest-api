package ru.invest.api.alert.service.usecase;

import java.time.LocalDate;

/**
 * Проверка правил по цене и доходности - после каждого обновления цен.
 */
public interface PriceAlertCheckUseCase {
    /**
     * Все включённые правила по цене и доходности.
     *
     * @param today «сегодня» по Москве: изменение за день считается от закрытия последнего торгового дня до него
     * @return сколько уведомлений поставлено в очередь
     */
    int checkAll(LocalDate today);

    /**
     * Одно правило - сразу после создания или изменения.
     */
    int check(Long alertId, LocalDate today);
}
