package ru.invest.api.tinkoff.supplier.constants;

import java.util.Set;

public interface Constants {
    String COUPON_EXECUTOR_SERVICE = "couponExecutorService";

    /**
     * Насколько вперёд запрашивать купоны и события облигаций. Без явного периода GetBondCoupons отдаёт будущие купоны
     * только на год вперёд, а период по умолчанию GetBondEvents не описан - берём с запасом на самые длинные выпуски.
     */
    int BOND_SCHEDULE_HORIZON_YEARS = 50;
    /**
     * Насколько назад запрашивать купоны: прошлые выплаты показываются в графике карточки облигации.
     * Столько же GetBondCoupons отдаёт без явного периода.
     */
    int PAST_COUPONS_YEARS = 3;

    Set<String> FOREIGN_CURRENCIES = Set.of("EUR", "USD", "CNY");
    Set<String> RU_CURRENCIES = Set.of("RUB", "RUR");
    String RU_PREFIX = "RU";
}
