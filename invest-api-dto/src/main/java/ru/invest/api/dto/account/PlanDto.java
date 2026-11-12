package ru.invest.api.dto.account;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

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
}
