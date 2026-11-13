package ru.invest.api.dto.account;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Тариф: лимиты и включённые фичи.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PlanDto {
    /** Постоянный id из конфигурации */
    private String id;
    private String title;
    private String description;
    /** Платный тариф */
    private boolean paid;
    /** Лимит - сколько можно создать */
    private Map<PlanLimitDto, Integer> limits;
    /** Включённые фичи */
    private List<PlanFeatureDto> features;
    /** Цена за срок period, рубли; null - тариф не продаётся (выдаётся только вручную) */
    private BigDecimal price;
    /** Сколько даёт одна оплата, ISO-8601: P1M, P1Y; null - не продаётся */
    private String period;
    /** Срок по-русски: «1 месяц» */
    private String periodTitle;
    /** Можно купить: есть цена и срок */
    private boolean purchasable;
}
