package ru.invest.api.news.supplier.usecase;

import ru.invest.api.news.supplier.model.OfzYieldCurve;

import java.util.Map;
import java.util.Optional;

/**
 * Данные облигаций с Мосбиржи для скринера: кривая доходности ОФЗ и описание купона выпусков.
 */
public interface MoexBondDataUseCase {
    /**
     * Последняя кривая бескупонной доходности ОФЗ; биржа не ответила - пусто (кэшируется час, пустой ответ - нет).
     */
    Optional<OfzYieldCurve> getOfzCurve();

    /**
     * Описание купона выпусков (COUPON_DETAILS): ISIN - «Ключевая ставка», «Ставка RUONIA», «Фиксированный с известными
     * купонами»...; выпуски без описания в ответ не попадают; биржа не ответила - пустая карта.
     */
    Map<String, String> getCouponDetails();
}
