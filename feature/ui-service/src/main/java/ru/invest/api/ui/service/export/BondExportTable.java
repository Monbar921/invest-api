package ru.invest.api.ui.service.export;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.enums.RiskLevel;
import ru.invest.api.common.model.rating.CreditRatingModel;
import ru.invest.api.common.model.rating.RatingAgency;
import ru.invest.api.common.model.screener.LiquidityModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Таблица выгрузки списка облигаций: столбцы и значения ячеек, общие для CSV и Excel. Пустая ячейка - значения нет
 * (как null в API), а не ноль.
 */
public final class BondExportTable {
    private static final int MONEY_SCALE = 2;

    private static final List<Column> COLUMNS = List.of(
            text("Тикер", BondModel::getTicker),
            text("ISIN", BondModel::getIsin),
            text("Название", BondModel::getName),
            text("Сектор", BondModel::getSector),
            text("Валюта", BondModel::getCurrency),
            number("Цена, % номинала", bond -> price(bond, PriceModel::getPercentagePrice)),
            number("Цена, ₽", bond -> money(price(bond, PriceModel::getCurrentInRub))),
            number("Купонная доходность, %", bond -> coupon(bond, CouponModel::getInterest)),
            number("Выплат купона в год", bond -> Optional.ofNullable(bond.getCoupon())
                    .map(coupon -> BigDecimal.valueOf(coupon.getQuantityPerYear())).orElse(null)),
            text("Купон", bond -> Optional.ofNullable(bond.getCoupon()).map(CouponModel::getIsFixed)
                    .map(fixed -> fixed ? "постоянный" : "переменный").orElse(null)),
            text("База флоатера", BondModel::getFloaterBaseRate),
            number("Доходность к погашению, %", bond -> coupon(bond, CouponModel::getYieldToMaturity)),
            number("Доходность к оферте, %", bond -> coupon(bond, CouponModel::getYieldToOffer)),
            number("Дюрация, лет", bond -> coupon(bond, CouponModel::getDurationMacaulay)),
            number("Модифицированная дюрация", bond -> coupon(bond, CouponModel::getDurationModified)),
            date("Дата погашения", bond -> toDate(bond.getMaturityDate())),
            date("Дата оферты", bond -> toDate(bond.getOfferDate())),
            text("Амортизация", bond -> bond.isAmortizationFlag() ? "да" : "нет"),
            text("Рейтинг", bond -> rating(bond, CreditRatingModel::getGrade)),
            text("Рейтинг агентства", bond -> rating(bond, BondExportTable::agencyRating)),
            number("Спред к ОФЗ, п.п.", BondModel::getSpreadToOfz),
            number("Средний дневной оборот за 30 дней, ₽", bond -> Optional.ofNullable(bond.getLiquidity())
                    .map(LiquidityModel::getAvgDailyTurnoverRub).orElse(null)),
            text("Риск T-Invest", bond -> risk(bond.getRiskLevel())));

    private BondExportTable() {
    }

    public static List<Column> columns() {
        return COLUMNS;
    }

    private static Column text(final String title, final Function<BondModel, String> value) {
        return new Column(title, CellType.TEXT, value::apply);
    }

    private static Column number(final String title, final Function<BondModel, BigDecimal> value) {
        return new Column(title, CellType.NUMBER, value::apply);
    }

    private static Column date(final String title, final Function<BondModel, LocalDate> value) {
        return new Column(title, CellType.DATE, value::apply);
    }

    private static BigDecimal price(final BondModel bond, final Function<PriceModel, BigDecimal> value) {
        return Optional.ofNullable(bond.getPrice()).map(value).orElse(null);
    }

    private static BigDecimal coupon(final BondModel bond, final Function<CouponModel, BigDecimal> value) {
        return Optional.ofNullable(bond.getCoupon()).map(value).orElse(null);
    }

    private static String rating(final BondModel bond, final Function<CreditRatingModel, String> value) {
        return Optional.ofNullable(bond.getCreditRating()).map(value).orElse(null);
    }

    private static String agencyRating(final CreditRatingModel rating) {
        return rating.getRating() + ", " + (rating.getAgency() == RatingAgency.ACRA ? "АКРА" : "НКР");
    }

    private static BigDecimal money(final BigDecimal value) {
        return value == null ? null : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static LocalDate toDate(final LocalDateTime value) {
        return value == null ? null : value.toLocalDate();
    }

    private static String risk(final RiskLevel riskLevel) {
        if (riskLevel == null) {
            return null;
        }
        return switch (riskLevel) {
            case RISK_LEVEL_LOW -> "низкий";
            case RISK_LEVEL_MODERATE -> "средний";
            case RISK_LEVEL_HIGH -> "высокий";
            case RISK_LEVEL_UNSPECIFIED, UNRECOGNIZED -> null;
        };
    }

    public enum CellType {
        TEXT,
        NUMBER,
        DATE
    }

    /**
     * Столбец: заголовок, тип ячеек и значение для облигации (String, BigDecimal или LocalDate; null - пусто).
     */
    public record Column(String title, CellType type, Function<BondModel, Object> value) {
    }
}
