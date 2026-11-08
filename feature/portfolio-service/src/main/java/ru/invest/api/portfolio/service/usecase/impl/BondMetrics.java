package ru.invest.api.portfolio.service.usecase.impl;

import java.math.BigDecimal;

/**
 * Показатели облигации для строки портфеля: купон и доходности. Сами считаются в use case по загруженным облигациям,
 * свод только спрашивает их по тикеру.
 */
interface BondMetrics {
    /** Купон, % годовых от номинала; null - посчитать нельзя */
    BigDecimal couponRate(String ticker);

    /** Доходность к погашению по текущей цене, % годовых */
    BigDecimal currentYieldToMaturity(String ticker);

    /** Доходность к погашению, если купить сегодня по цене pricePercent (% от номинала) */
    BigDecimal yieldToMaturityAtPrice(String ticker, BigDecimal pricePercent);
}
