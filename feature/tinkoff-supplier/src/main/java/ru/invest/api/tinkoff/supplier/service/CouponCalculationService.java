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

    /**
     * Рассчитывает эффективную доходность к погашению в процентах годовых - ставку, при которой сумма будущих
     * выплат, дисконтированных по (1 + r)^(дней / 365), равна цене покупки (текущая цена плюс НКД).
     * Выплаты - купоны, частичные погашения номинала по графику амортизации и остаток номинала в дату погашения.
     *
     * @return процентная доходность или null, если расчёт невозможен
     */
    BigDecimal calculateYieldToMaturity(CouponModel couponModel, BondModel bondModel);

    /**
     * Доходность к погашению вместе с дюрацией Маколея и модифицированной: дюрация - средний срок выплат того же
     * расчёта, взвешенный по их стоимости, приведённой по найденной ставке.
     *
     * @return метрики; если доходность посчитать нельзя - все поля null
     */
    YieldMetrics calculateMaturityMetrics(CouponModel couponModel, BondModel bondModel);

    /**
     * Рассчитывает эффективную доходность к ближайшей оферте - так же, как к погашению, но выплаты учитываются
     * до даты оферты, а в дату оферты облигация выкупается по остатку номинала.
     *
     * @return процентная доходность или null, если оферты нет или расчёт невозможен
     */
    BigDecimal calculateYieldToOffer(CouponModel couponModel, BondModel bondModel);
}
