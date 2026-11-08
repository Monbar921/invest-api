package ru.invest.api.currency.service.usecase;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Курсы ЦБ к рублю на дату. Порядок: кэш в памяти → таблица currency_rate → ЦБ. Курсы ЦБ на дату, которой ещё нет
 * в таблице, сохраняются все сразу (один ответ ЦБ - все валюты), если дата не позже сегодняшней по Москве.
 */
public interface CurrencyRateHistoryUseCase {
    /**
     * Сколько рублей стоит одна единица валюты на дату; для рубля - 1.
     *
     * @return null - у ЦБ нет такой валюты на эту дату
     * @throws feign.FeignException ЦБ недоступен (результат не кэшируется и не сохраняется)
     */
    BigDecimal getRubRate(String currency, LocalDate date);
}
