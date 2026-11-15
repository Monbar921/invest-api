package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Открытая позиция портфеля с данными облигации - вход расчёта аналитики. null у любого поля - данных нет.
 *
 * @param valueInRub       текущая стоимость непроданного остатка в рублях (цена без НКД, курс ЦБ на сегодня);
 *                         null - позицию нельзя оценить, она не входит в доли (см. unvaluedReason)
 * @param unvaluedReason   почему нет оценки; null - оценка есть
 * @param issuerId         id эмитента на Мосбирже
 * @param issuerTitle      короткое название эмитента
 * @param ratingGrade      ступень общей шкалы рейтинга («AA-»)
 * @param ratingScore      балл общей шкалы (D - 0, AAA - 20); null - рейтинга нет
 * @param fixedCoupon      true - постоянный купон, false - флоатер
 * @param yieldToMaturity  доходность к погашению по текущей цене, % годовых
 * @param durationMacaulay дюрация Маколея, лет
 * @param durationModified модифицированная дюрация, лет
 */
public record AnalyticsPositionModel(String ticker,
                                     String bondName,
                                     BigDecimal valueInRub,
                                     UnvaluedReason unvaluedReason,
                                     Long issuerId,
                                     String issuerTitle,
                                     String sector,
                                     String ratingGrade,
                                     Integer ratingScore,
                                     String currency,
                                     Boolean fixedCoupon,
                                     LocalDate maturityDate,
                                     BigDecimal yieldToMaturity,
                                     BigDecimal durationMacaulay,
                                     BigDecimal durationModified) {
}
