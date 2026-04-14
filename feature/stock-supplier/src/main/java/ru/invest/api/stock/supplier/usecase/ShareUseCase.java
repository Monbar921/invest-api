package ru.invest.api.stock.supplier.usecase;

import java.math.BigDecimal;

public interface ShareUseCase {
    BigDecimal getCommonSharePrice(String ticker);
}
