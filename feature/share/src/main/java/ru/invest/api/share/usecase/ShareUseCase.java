package ru.invest.api.share.usecase;

import java.math.BigDecimal;

public interface ShareUseCase {
    BigDecimal getCommonSharePrice(String ticker);
}
