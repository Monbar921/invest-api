package ru.invest.api.dto.portfolio.imports;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.util.List;

/** Предпросмотр импорта по токену */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class TinvestImportRequest {
    /** Счета; пусто - все счета токена */
    private List<String> accountIds;
    /** С какой даты; не задана - вся история и остатки позиций без истории */
    private LocalDate from;
}
