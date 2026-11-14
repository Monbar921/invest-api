package ru.invest.api.dto.portfolio.imports;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/** Счёт в Т-Инвестициях, доступный токену */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BrokerAccountDto {
    private String id;
    private String name;
    /** ACCOUNT_TYPE_TINKOFF, ACCOUNT_TYPE_TINKOFF_IIS... */
    private String type;
    private LocalDate openedDate;
    private boolean closed;
}
