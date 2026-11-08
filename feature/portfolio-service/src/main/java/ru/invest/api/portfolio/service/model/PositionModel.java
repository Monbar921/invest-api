package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Позиция портфеля с текущей оценкой. Суммы - в валюте номинала, на всё количество.
 *
 * @param bondName       null - облигации уже нет в системе
 * @param currency       валюта номинала; null - облигации нет
 * @param broker         имя брокера; null - не указан
 * @param nominal        текущий номинал одной облигации; null - облигации или цены нет
 * @param buyPrice       цена покупки, % от номинала
 * @param currentPrice   текущая цена, % от номинала; null - цены нет
 * @param buyAmount      номинал × цена покупки × количество
 * @param currentAmount  номинал × текущая цена × количество; null - цены нет
 * @param revenue        текущая стоимость − стоимость покупки, отрицательный - убыток; null - цены нет
 * @param revenuePercent доход в % к стоимости покупки; null - цены нет
 * @param createdAt      UTC
 */
public record PositionModel(Long id,
                            String ticker,
                            String bondName,
                            String isin,
                            String currency,
                            int quantity,
                            LocalDate buyDate,
                            String broker,
                            BigDecimal nominal,
                            BigDecimal buyPrice,
                            BigDecimal currentPrice,
                            BigDecimal buyAmount,
                            BigDecimal currentAmount,
                            BigDecimal revenue,
                            BigDecimal revenuePercent,
                            LocalDateTime createdAt) {
}
