package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;

/**
 * Итог портфеля в одной валюте номинала - позиции в разных валютах не складываются. Позиции без цены не входят.
 *
 * @param revenuePercent     доход в % к стоимости покупки
 * @param buyAmountInRub     сумма стоимостей покупки в рублях по курсам на даты покупок
 * @param currentAmountInRub сумма текущих стоимостей в рублях по курсу на сегодня
 * @param revenueInRub       текущая стоимость в рублях − стоимость покупки в рублях;
 *                           все три null, если хотя бы у одной покупки итога нет курса
 */
public record PortfolioTotalModel(String currency,
                                  BigDecimal buyAmount,
                                  BigDecimal currentAmount,
                                  BigDecimal revenue,
                                  BigDecimal revenuePercent,
                                  BigDecimal buyAmountInRub,
                                  BigDecimal currentAmountInRub,
                                  BigDecimal revenueInRub) {
}
