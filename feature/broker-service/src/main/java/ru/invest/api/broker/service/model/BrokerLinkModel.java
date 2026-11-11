package ru.invest.api.broker.service.model;

/**
 * Ссылка на покупку облигации у брокера-партнёра с данными для маркировки рекламы.
 *
 * @param partnerId  id партнёра из конфигурации
 * @param name       название брокера
 * @param url        ссылка по шаблону партнёра
 * @param advertiser рекламодатель: наименование и ИНН
 * @param erid       токен рекламы или null
 */
public record BrokerLinkModel(String partnerId, String name, String url, String advertiser, String erid) {
}
