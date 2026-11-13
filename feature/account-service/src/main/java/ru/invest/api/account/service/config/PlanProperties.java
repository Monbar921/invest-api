package ru.invest.api.account.service.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import ru.invest.api.common.model.account.PlanFeature;
import ru.invest.api.common.model.account.PlanLimit;

import java.math.BigDecimal;
import java.time.Period;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Тарифы: лимиты и фичи. Порядок тарифов в настройке - порядок на странице «Тариф». Ошибка в настройке (тарифа по
 * умолчанию нет среди тарифов, у тарифа задан не каждый лимит, неверный id) не даёт приложению стартовать.
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "ru.invest.api.plans")
public class PlanProperties {
    private static final Pattern ID = Pattern.compile("[a-z0-9-]{1,64}");

    /** Тариф без подписки и после её окончания */
    @NotBlank
    private String defaultPlan;

    /** Часовой пояс дня окончания подписки: «до 31 января» - до конца 31 января по этому поясу */
    @NotNull
    private ZoneId zone = ZoneId.of("Europe/Moscow");

    /** id тарифа - тариф. id хранится в подписках: не менять у выданных тарифов */
    @Valid
    @NotEmpty
    private Map<String, Plan> plans = new LinkedHashMap<>();

    @AssertTrue(message = "default-plan должен быть среди plans")
    public boolean isDefaultPlanKnown() {
        return defaultPlan == null || plans.containsKey(defaultPlan);
    }

    @AssertTrue(message = "id тарифа: латиница в нижнем регистре, цифры и дефис, до 64 символов")
    public boolean isPlanIdsValid() {
        return plans.keySet().stream().allMatch(id -> ID.matcher(id).matches());
    }

    @AssertTrue(message = "у каждого тарифа должны быть заданы все лимиты, не меньше 0")
    public boolean isLimitsComplete() {
        return plans.values().stream().allMatch(plan -> plan.getLimits().keySet().containsAll(EnumSet.allOf(PlanLimit.class))
                && plan.getLimits().values().stream().allMatch(max -> max != null && max >= 0));
    }

    @AssertTrue(message = "цена задаётся только платному тарифу: больше 0, вместе со сроком period (P1M, P1Y)")
    public boolean isPricesValid() {
        return plans.values().stream().allMatch(plan -> plan.getPrice() == null && plan.getPeriod() == null
                || plan.isPaid() && plan.getPrice() != null && plan.getPrice().signum() > 0
                && plan.getPeriod() != null && !plan.getPeriod().isNegative() && !plan.getPeriod().isZero());
    }

    @Data
    public static class Plan {
        /** Название для пользователя */
        @NotBlank
        private String title;

        /** Короткое описание на странице «Тариф» */
        private String description;

        /** Платный тариф. Без цены выдаётся только вручную */
        private boolean paid;

        /** Цена за срок period, рубли; null - тариф не продаётся (только ручная выдача) */
        private BigDecimal price;

        /** Сколько даёт одна оплата: P1M - месяц, P1Y - год */
        private Period period;

        /** Лимит - сколько можно создать */
        private Map<PlanLimit, Integer> limits = new EnumMap<>(PlanLimit.class);

        /** Включённые фичи; фичи нет в списке - она закрыта */
        private Set<PlanFeature> features = EnumSet.noneOf(PlanFeature.class);
    }
}
