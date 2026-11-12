package ru.invest.api.account.service.model;

import java.time.LocalDate;

/**
 * Выдача тарифа вручную.
 *
 * @param plan       id тарифа из конфигурации, не тариф по умолчанию
 * @param validUntil последний день включительно (в поясе тарифов); null - бессрочно
 * @param comment    зачем выдан: для истории
 */
public record GrantPlanModel(String plan, LocalDate validUntil, String comment) {
}
