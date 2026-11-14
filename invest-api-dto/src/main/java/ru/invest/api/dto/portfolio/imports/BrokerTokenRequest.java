package ru.invest.api.dto.portfolio.imports;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Токен T-Invest API только на чтение. Формат проверяет invest-api (422) - без bean validation, чтобы значение не
 * попало в лог ошибок валидации; toString токен не показывает.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BrokerTokenRequest {
    @ToString.Exclude
    private String token;
}
