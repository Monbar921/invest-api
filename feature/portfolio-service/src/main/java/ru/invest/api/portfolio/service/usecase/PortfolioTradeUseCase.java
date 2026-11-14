package ru.invest.api.portfolio.service.usecase;

import ru.invest.api.portfolio.service.model.SaleRequestModel;
import ru.invest.api.portfolio.service.model.TradeModel;

import java.util.List;

/**
 * Сделки портфеля: продажи (CRUD) и список всех сделок. Чужая или несуществующая продажа - 404.
 */
public interface PortfolioTradeUseCase {
    /**
     * Покупки, продажи и погашения пользователя, новые первыми.
     */
    List<TradeModel> getTrades(String userId);

    /**
     * @throws ru.invest.api.common.exception.GeneralNotFoundEntityException     облигации нет
     * @throws ru.invest.api.common.exception.GeneralUnprocessableEntityException нет тикера, количество, цена или дата
     *                                                                            вне пределов, продажа не раньше
     *                                                                            погашения, облигаций в покупках на
     *                                                                            эту дату не хватает
     */
    TradeModel createSale(String userId, SaleRequestModel request);

    /**
     * Продажа с ключом сделки у источника импорта - те же проверки, что у {@link #createSale(String, SaleRequestModel)}.
     *
     * @param importKey ключ сделки у источника; null - введена вручную
     */
    TradeModel createSale(String userId, SaleRequestModel request, String importKey);

    /**
     * Меняет количество, цену, дату, НКД, комиссию и брокера; тикер из запроса не используется.
     */
    TradeModel updateSale(String userId, Long id, SaleRequestModel request);

    void deleteSale(String userId, Long id);
}
