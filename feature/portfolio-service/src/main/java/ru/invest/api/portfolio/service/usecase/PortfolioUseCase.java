package ru.invest.api.portfolio.service.usecase;

import ru.invest.api.portfolio.service.model.BrokerModel;
import ru.invest.api.portfolio.service.model.PortfolioModel;
import ru.invest.api.portfolio.service.model.PositionModel;
import ru.invest.api.portfolio.service.model.PositionRequestModel;

import java.util.List;

/**
 * Портфель пользователя. Чужая или несуществующая позиция - GeneralNotFoundEntityException (404).
 */
public interface PortfolioUseCase {
    PortfolioModel getPortfolio(String userId);

    /**
     * @throws ru.invest.api.common.exception.GeneralNotFoundEntityException     облигации нет
     * @throws ru.invest.api.common.exception.GeneralUnprocessableEntityException нет тикера, количество, цена или
     *                                                                            дата вне пределов, позиций больше 200,
     *                                                                            брокеров больше 50, рублёвая
     *                                                                            сумма у рублёвой облигации
     */
    PositionModel create(String userId, PositionRequestModel request);

    /**
     * Меняет количество, цену и дату покупки; тикер из запроса не используется.
     */
    PositionModel update(String userId, Long id, PositionRequestModel request);

    void delete(String userId, Long id);

    /**
     * Брокеры пользователя по имени - и те, у которых покупок уже нет.
     */
    List<BrokerModel> getBrokers(String userId);
}
