package ru.invest.api.dto.bond.broker;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Ссылка на покупку облигации у брокера-партнёра. Это реклама: показывается с пометкой «Реклама», рекламодателем
 * и erid.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BrokerLinkDto {
    /** id партнёра: по нему отправляется клик */
    private String partnerId;
    /** Название брокера */
    private String name;
    /** Реферальная ссылка */
    private String url;
    /** Рекламодатель: наименование и ИНН */
    private String advertiser;
    /** Токен рекламы из ЕРИР, null - нет */
    private String erid;
}
