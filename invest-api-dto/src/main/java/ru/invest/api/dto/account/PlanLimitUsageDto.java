package ru.invest.api.dto.account;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Лимит действующего тарифа и сколько использовано. used может быть больше max: созданное на прошлом тарифе не
 * удаляется, но новое не создаётся.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PlanLimitUsageDto {
    private PlanLimitDto limit;
    private int max;
    private long used;
}
