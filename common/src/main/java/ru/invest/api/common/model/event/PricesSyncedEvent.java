package ru.invest.api.common.model.event;

import java.util.Set;

/**
 * Цены облигаций обновлены (частая синхронизация, шаг цен ночной цепочки, ручной запуск). По нему проверяются правила
 * уведомлений по цене и доходности; модуль синхронизации об уведомлениях не знает.
 *
 * @param uids облигации, чьи цены сохранены
 */
public record PricesSyncedEvent(Set<String> uids) {
}
