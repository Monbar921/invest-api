package ru.invest.api.ticker.service.usecase;

import java.math.BigDecimal;

public interface ShareUseCase {
    BigDecimal getSharePrice(String ticker, String classCode);
}
