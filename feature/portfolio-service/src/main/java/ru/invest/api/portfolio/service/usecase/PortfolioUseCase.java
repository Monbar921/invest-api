package ru.invest.api.portfolio.service.usecase;

import ru.invest.api.portfolio.service.model.BrokerModel;
import ru.invest.api.portfolio.service.model.PortfolioModel;
import ru.invest.api.portfolio.service.model.PositionModel;
import ru.invest.api.portfolio.service.model.PositionRequestModel;

import java.math.BigDecimal;
import java.util.List;

/**
 * Портфель пользователя. Чужая или несуществующая позиция - GeneralNotFoundEntityException (404).
 */
public interface PortfolioUseCase {
    /**
     * Открытые позиции с доходом от цены и полным доходом, закрытые позиции, итоги по валютам и весь портфель в рублях.
     *
     * @param taxRatePercent ставка НДФЛ для полного дохода, %; null - налог не считается
     * @throws ru.invest.api.common.exception.GeneralUnprocessableEntityException ставка вне 0..100
     */
    PortfolioModel getPortfolio(String userId, BigDecimal taxRatePercent);

    /**
     * @throws ru.invest.api.common.exception.GeneralNotFoundEntityException     облигации нет
     * @throws ru.invest.api.common.exception.GeneralUnprocessableEntityException нет тикера, количество, цена или
     *                                                                            дата вне пределов, брокеров больше
     *                                                                            50, рублёвая сумма у рублёвой
     *                                                                            облигации
     * @throws ru.invest.api.common.exception.GeneralPaymentRequiredException     покупок столько, сколько разрешает
     *                                                                            тариф
     */
    PositionModel create(String userId, PositionRequestModel request);

    /**
     * Покупка с ключом сделки у источника импорта - те же проверки, что у {@link #create(String, PositionRequestModel)}.
     *
     * @param importKey ключ сделки у источника; null - введена вручную
     */
    PositionModel create(String userId, PositionRequestModel request, String importKey);

    /**
     * Меняет количество, цену и дату покупки; тикер из запроса не используется.
     *
     * @throws ru.invest.api.common.exception.GeneralUnprocessableEntityException продажам облигации не хватило бы
     *                                                                            покупок
     */
    PositionModel update(String userId, Long id, PositionRequestModel request);

    /**
     * @throws ru.invest.api.common.exception.GeneralUnprocessableEntityException продажам облигации не хватило бы
     *                                                                            покупок
     */
    void delete(String userId, Long id);

    /**
     * Брокеры пользователя по имени - и те, у которых покупок уже нет.
     */
    List<BrokerModel> getBrokers(String userId);
}
