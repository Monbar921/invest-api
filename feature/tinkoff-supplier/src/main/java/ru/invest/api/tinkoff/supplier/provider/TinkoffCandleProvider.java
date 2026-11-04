package ru.invest.api.tinkoff.supplier.provider;

import ru.invest.api.common.model.candle.CandleModel;

import java.time.LocalDate;
import java.util.List;

public interface TinkoffCandleProvider {
    /**
     * Дневные свечи с даты from по дату to включительно, по возрастанию даты.
     */
    List<CandleModel> getDailyCandles(String uid, LocalDate from, LocalDate to);

    /**
     * Вся доступная история дневных свечей по дату to включительно: окна идут назад, пока API не вернёт пустое окно.
     */
    List<CandleModel> getDailyHistory(String uid, LocalDate to);
}
