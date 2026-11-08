package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;

/**
 * Весь портфель в рублях - сумма рублёвых сумм итогов по всем валютам. Позиции без текущей цены не входят, как и в
 * итоги по валютам.
 *
 * @param revenuePercent доход в рублях в % к стоимости покупки в рублях
 */
public record PortfolioSummaryModel(BigDecimal buyAmountInRub,
                                    BigDecimal currentAmountInRub,
                                    BigDecimal revenueInRub,
                                    BigDecimal revenuePercent) {
}
