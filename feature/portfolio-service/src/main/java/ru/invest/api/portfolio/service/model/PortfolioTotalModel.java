package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;

/**
 * Итог портфеля в одной валюте номинала - позиции в разных валютах не складываются. Позиции без цены не входят.
 *
 * @param revenuePercent доход в % к стоимости покупки
 */
public record PortfolioTotalModel(String currency,
                                  BigDecimal buyAmount,
                                  BigDecimal currentAmount,
                                  BigDecimal revenue,
                                  BigDecimal revenuePercent) {
}
