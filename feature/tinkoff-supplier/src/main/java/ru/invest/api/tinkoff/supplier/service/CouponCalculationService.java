package ru.invest.api.tinkoff.supplier.service;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;

import java.math.BigDecimal;

public interface CouponCalculationService {

    /**
     * Рассчитывает годовую доходность купона в процентах относительно текущей цены облигации.
     * Конвертирует валюты купонов при необходимости.
     *
     * @return процентная доходность или null, если расчёт невозможен
     */
    BigDecimal calculateInterest(CouponModel couponModel, BondModel bondModel);
}
