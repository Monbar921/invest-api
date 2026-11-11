package ru.invest.api.broker.service.usecase;

import ru.invest.api.broker.service.model.BrokerClickStatModel;
import ru.invest.api.broker.service.model.BrokerLinkModel;

import java.time.LocalDate;
import java.util.List;

/**
 * Реферальные ссылки брокеров-партнёров и учёт переходов.
 */
public interface BrokerLinkUseCase {
    /**
     * Ссылки включённых партнёров для облигации в порядке конфигурации. Партнёров нет - пустой список, облигация не
     * ищется.
     *
     * @throws ru.invest.api.common.exception.GeneralNotFoundEntityException облигации нет
     */
    List<BrokerLinkModel> getLinks(String ticker);

    /**
     * +1 переход к партнёру по облигации за сегодня.
     *
     * @throws ru.invest.api.common.exception.GeneralNotFoundEntityException партнёра нет или он выключен, облигации нет
     */
    void registerClick(String ticker, String partnerId);

    /**
     * Переходы по дням с from по to включительно.
     */
    List<BrokerClickStatModel> getClicks(LocalDate from, LocalDate to);
}
