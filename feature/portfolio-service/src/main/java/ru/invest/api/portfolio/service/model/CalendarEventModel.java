package ru.invest.api.portfolio.service.model;

import ru.invest.api.common.model.event.BondEventType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Выплата по облигации портфеля: событие облигации × количество облигаций у пользователя.
 *
 * @param currency              валюта выплаты; размер не объявлен - валюта номинала
 * @param quantity              сколько облигаций в портфеле (все покупки этой облигации)
 * @param amountPerBond         выплата на одну облигацию, как во вкладке «События» карточки; null - не объявлена или
 *                              у события выплаты нет (оферта, погашение, чей остаток номинала - в амортизации того же дня)
 * @param amount                amountPerBond × quantity
 * @param amountInRub           amount по курсу на сегодня; null - нет суммы или курса
 * @param amountUnknown         выплата будет, но размер неизвестен (купон не объявлен, нет номинала): итог неполный
 * @param previousAmountPerBond прошлый размер купона, если он изменился (пересмотр ставки флоатера)
 */
public record CalendarEventModel(LocalDate date,
                                 BondEventType type,
                                 String ticker,
                                 String bondName,
                                 String currency,
                                 long quantity,
                                 BigDecimal amountPerBond,
                                 BigDecimal amount,
                                 BigDecimal amountInRub,
                                 boolean amountUnknown,
                                 BigDecimal previousAmountPerBond) {
}
