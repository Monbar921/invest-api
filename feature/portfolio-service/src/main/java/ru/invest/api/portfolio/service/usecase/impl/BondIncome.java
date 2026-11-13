package ru.invest.api.portfolio.service.usecase.impl;

import ru.invest.api.portfolio.service.model.TradeModel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Денежные потоки по одной облигации портфеля - из них складывается её полный доход и доход итогов.
 *
 * @param bondKnown          облигация есть в системе; нет - доход не считается
 * @param currency           валюта номинала; null - облигации нет
 * @param flows              потоки в валюте номинала (и в рублях по курсу на их дату)
 * @param openQuantity       сколько облигаций не продано и не погашено
 * @param valueKnown         стоимость непроданных известна: их нет или есть текущая цена
 * @param unknownCouponCount полученные купоны без размера в графике
 * @param incomplete         НКД не посчитать, покупка раньше хранимой истории выплат или нет графика амортизации
 * @param closeDate          последняя продажа или погашение; null - не было
 * @param trades             покупки, продажи и погашение
 */
public record BondIncome(String ticker,
                         boolean bondKnown,
                         String currency,
                         List<Flow> flows,
                         int openQuantity,
                         boolean valueKnown,
                         int unknownCouponCount,
                         boolean incomplete,
                         LocalDate closeDate,
                         List<TradeModel> trades) {

    /** У всех потоков есть сумма в рублях */
    boolean rubKnown() {
        return flows.stream().map(Flow::amountInRub).allMatch(Objects::nonNull);
    }

    /** Вид потока; sign - знак в доходе: вложения отрицательные, полученное положительное */
    public enum FlowKind {
        BUY(-1),
        BUY_ACI(-1),
        BUY_COMMISSION(-1),
        COUPON(1),
        AMORTIZATION(1),
        SALE(1),
        SALE_ACI(1),
        SALE_COMMISSION(-1),
        REDEMPTION(1),
        /** Стоимость непроданных облигаций по текущей цене на сегодня */
        VALUE(1),
        /** НКД непроданных облигаций на сегодня */
        VALUE_ACI(1);

        private final int sign;

        FlowKind(final int sign) {
            this.sign = sign;
        }

        BigDecimal signed(final BigDecimal amount) {
            return sign < 0 ? amount.negate() : amount;
        }
    }

    /**
     * @param amount      сумма в валюте номинала, без знака
     * @param amountInRub сумма в рублях по курсу ЦБ на дату потока (стоимость сейчас - по курсу на сегодня); null -
     *                    курса нет
     */
    public record Flow(LocalDate date, FlowKind kind, BigDecimal amount, BigDecimal amountInRub) {
    }
}
