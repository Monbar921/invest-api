package ru.invest.api.bond.supplier.collection;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ru.invest.api.common.model.parameters.BondParametersRequestModel;
import ru.invest.api.common.model.parameters.BondSortField;
import ru.invest.api.common.model.parameters.BondSortModel;
import ru.invest.api.common.model.parameters.BondSortOrder;
import ru.invest.api.common.model.parameters.DateRangeModel;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Подборки облигаций - готовые фильтры с постоянным адресом (для поиска и ссылок).
 * Тексты только описывают выборку: никаких оценок «стоит купить».
 */
@Getter
@RequiredArgsConstructor
public enum BondCollection {
    OFZ_TOP_YIELD("ofz-top-yield", "Самые доходные ОФЗ",
            "Облигации федерального займа по доходности к погашению: сверху - с наибольшей. Доходность считается "
                    + "по последней цене с учётом НКД; выпуски, у которых её посчитать нельзя, - в конце.") {
        @Override
        public BondParametersRequestModel parameters(final LocalDate today) {
            final BondParametersRequestModel parameters = new BondParametersRequestModel();
            parameters.setIsOfz(true);
            parameters.setBondSorts(List.of(sort(BondSortField.YIELD_TO_MATURITY, BondSortOrder.DESC)));
            return parameters;
        }
    },
    FLOATERS("floaters", "Флоатеры - облигации с переменным купоном",
            "Облигации, у которых размер купона меняется: обычно он привязан к ключевой ставке, RUONIA или "
                    + "инфляции. Отсортированы по текущей купонной доходности.") {
        @Override
        public BondParametersRequestModel parameters(final LocalDate today) {
            final BondParametersRequestModel parameters = new BondParametersRequestModel();
            parameters.setIsFixedCoupon(false);
            parameters.setBondSorts(List.of(sort(BondSortField.COUPON_INTEREST, BondSortOrder.DESC)));
            return parameters;
        }
    },
    MATURITY_THIS_YEAR("maturity-this-year", "Облигации с погашением в этом году",
            "Выпуски, которые гасятся с сегодняшнего дня до конца текущего года, по дате погашения.") {
        @Override
        public BondParametersRequestModel parameters(final LocalDate today) {
            final BondParametersRequestModel parameters = new BondParametersRequestModel();
            parameters.setMaturityDate(new DateRangeModel()
                    .setFrom(today)
                    .setTo(today.with(TemporalAdjusters.lastDayOfYear())));
            parameters.setBondSorts(List.of(sort(BondSortField.MATURITY_DATE, BondSortOrder.ASC)));
            return parameters;
        }
    };

    /** Адрес подборки: /collections/{slug} */
    private final String slug;
    private final String title;
    private final String description;

    /**
     * Фильтры и сортировка подборки на дату today.
     */
    public abstract BondParametersRequestModel parameters(LocalDate today);

    public static Optional<BondCollection> bySlug(final String slug) {
        return Arrays.stream(values()).filter(collection -> collection.slug.equals(slug)).findFirst();
    }

    private static BondSortModel sort(final BondSortField field, final BondSortOrder order) {
        return new BondSortModel().setSortField(field).setSortOrder(order);
    }
}
