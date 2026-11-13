package ru.invest.api.dto.account;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * Тариф пользователя.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class AccountPlanDto {
    /** Действующий тариф */
    private PlanDto plan;
    /** Тариф по умолчанию: подписки нет или она закончилась */
    private boolean defaultPlan;
    /** Откуда подписка: MANUAL - выдана вручную, PAYMENT - оплачена; null - тариф по умолчанию */
    private String source;
    /** Последний день подписки включительно; null - бессрочно или тариф по умолчанию */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validUntil;
    /** Лимиты действующего тарифа и их использование */
    private List<PlanLimitUsageDto> limits;
    /** Все тарифы - для сравнения */
    private List<PlanDto> plans;
    /** Оплата: включена ли, оферта и политика, автопродление */
    private PaymentsInfoDto payments;
}
