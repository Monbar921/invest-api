package ru.invest.api.dto.bond.event;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import ru.invest.api.dto.bond.MoneyDto;

import java.time.LocalDate;

/**
 * Событие облигации из её графика выплат и дат. amount - выплата на одну облигацию, null - размер не объявлен
 * или выплаты нет (оферта); previousAmount - прошлый размер купона, если он изменился.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BondEventDto {
    private BondEventTypeDto type;
    private LocalDate date;
    private MoneyDto amount;
    private MoneyDto previousAmount;
}
