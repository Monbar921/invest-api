package ru.invest.api.portfolio.service.usecase;

import ru.invest.api.portfolio.service.model.PortfolioAnalyticsModel;

/**
 * Аналитика открытых позиций портфеля: доли по эмитентам, секторам, рейтингам, валютам и типу купона, лестница
 * погашений, средневзвешенные доходность и дюрация, концентрация и чувствительность к ставке.
 */
public interface PortfolioAnalyticsUseCase {
    PortfolioAnalyticsModel getAnalytics(String userId);
}
