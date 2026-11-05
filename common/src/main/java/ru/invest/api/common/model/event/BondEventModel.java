package ru.invest.api.common.model.event;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import ru.invest.api.common.model.MoneyModel;

import java.time.LocalDate;

/**
 * Событие по облигации. amount - выплата на одну облигацию: купон или часть номинала; null - размер ещё не объявлен
 * или у события выплаты нет (оферта).
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BondEventModel {
    private BondEventType type;
    private LocalDate date;
    private MoneyModel amount;
    /** Купон прошлой выплаты, если размер изменился: у плавающего купона - пересмотр ставки */
    private MoneyModel previousAmount;
}
