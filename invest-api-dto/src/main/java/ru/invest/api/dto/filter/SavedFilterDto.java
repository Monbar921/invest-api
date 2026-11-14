package ru.invest.api.dto.filter;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;
import ru.invest.api.dto.request.bond.BondParametersRequest;

import java.time.ZonedDateTime;

/**
 * Сохранённый фильтр списка облигаций.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class SavedFilterDto {
    private Long id;
    private String name;
    private BondScopeDto scope;
    /** Фильтры и сортировка - как в теле POST /bonds/{scope}/page */
    private BondParametersRequest parameters;
    /** Включены ли уведомления о новых облигациях в выборке */
    private boolean notify;
    /**
     * Правило уведомлений по фильтру (тип SAVED_FILTER); null - уведомления выключены. Правило, выключенное кнопкой в
     * Telegram, остаётся: alertId есть, notify - false
     */
    private Long alertId;
    /** Сколько облигаций в выборке сейчас */
    private Integer matches;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime createdAt;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime updatedAt;
}
