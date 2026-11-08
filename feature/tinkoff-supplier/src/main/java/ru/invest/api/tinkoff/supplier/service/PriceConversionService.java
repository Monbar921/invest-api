package ru.invest.api.tinkoff.supplier.service;

import ru.invest.api.common.model.BondModel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;

public interface PriceConversionService {

    /**
     * Заполняет у облигаций текущую цену в рублях (PriceModel.currentInRub) по курсу ЦБ,
     * при его отсутствии - по budget.org. Курс запрашивается один раз на каждую валюту.
     * Если цены или курса нет, currentInRub остаётся null - облигация считается без цены.
     */
    void fillCurrentInRub(Collection<BondModel> bonds);

    /**
     * Курс валюты к рублю на сегодня (ЦБ, иначе budget.org); для рубля - 1.
     *
     * @return null - курса нет (источники недоступны)
     */
    BigDecimal getRubRate(String currency);

    /**
     * Курс валюты к рублю по ЦБ на дату; для рубля - 1.
     *
     * @return null - курса нет (у ЦБ нет пары или ЦБ недоступен)
     */
    BigDecimal getRubRate(String currency, LocalDate date);
}
