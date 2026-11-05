package ru.invest.api.news.supplier.usecase;

import ru.invest.api.common.model.news.MarketOverviewModel;

/**
 * Ключевая ставка и курсы валют ЦБ для шапки ленты.
 */
public interface MarketOverviewUseCase {
    MarketOverviewModel getOverview();
}
