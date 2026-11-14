package ru.invest.api.dto.portfolio.imports;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.ZonedDateTime;
import java.util.List;

/** Подключение к T-Invest для импорта: сам токен не отдаётся */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BrokerConnectionDto {
    /** Импорт по токену включён на сервере */
    private boolean enabled;
    /** Токен сохранён */
    private boolean connected;
    /** Последние 4 символа токена */
    private String tokenHint;
    private ZonedDateTime connectedAt;
    private ZonedDateTime lastImportAt;
    /** Счета токена - в ответе на подключение и в /accounts; иначе null */
    private List<BrokerAccountDto> accounts;
}
