package ru.invest.api.tinkoff.supplier.constants;

import java.util.Set;

public interface Constants {
    String COUPON_EXECUTOR_SERVICE = "couponExecutorService";

    Set<String> FOREIGN_CURRENCIES = Set.of("EUR", "USD", "CNY");
    Set<String> RU_CURRENCIES = Set.of("RUB", "RUR");
    String RU_PREFIX = "RU";
}
