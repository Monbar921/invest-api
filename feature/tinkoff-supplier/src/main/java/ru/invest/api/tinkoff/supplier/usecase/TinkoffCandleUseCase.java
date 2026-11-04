package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.candle.CandleModel;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TinkoffCandleUseCase {
    Optional<LocalDate> findLastCandleDate(String uid);

    /**
     * Заменяет свечи облигации, начиная с даты первой из candles: старые с этой даты удаляются, новые записываются.
     */
    void replaceFrom(String uid, List<CandleModel> candles, AuditModel audit);

    /**
     * Дневные свечи по возрастанию даты; from и to включительно.
     */
    List<CandleModel> find(String uid, LocalDate from, LocalDate to);
}
