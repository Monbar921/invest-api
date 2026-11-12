package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import ru.invest.api.dto.bond.event.BondEventTypeDto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Выплата по облигации портфеля: событие облигации × количество облигаций у пользователя.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioCalendarEventDto {
    private LocalDate date;
    private BondEventTypeDto type;
    private String ticker;
    private String bondName;
    /** Валюта выплаты; размер не объявлен - валюта номинала */
    private String currency;
    /** Облигаций в портфеле - все покупки этой облигации */
    private long quantity;
    /** Выплата на одну облигацию, как во вкладке «События» карточки; null - не объявлена или выплаты нет (оферта) */
    private BigDecimal amountPerBond;
    /** amountPerBond × quantity */
    private BigDecimal amount;
    /** amount по курсу на сегодня; null - нет суммы или курса */
    private BigDecimal amountInRub;
    /** Выплата будет, но размер неизвестен (купон не объявлен): итог неполный */
    private boolean amountUnknown;
    /** Прошлый размер купона на одну облигацию, если он изменился */
    private BigDecimal previousAmountPerBond;
}
