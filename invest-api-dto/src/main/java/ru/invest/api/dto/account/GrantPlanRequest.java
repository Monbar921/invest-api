package ru.invest.api.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * Выдача тарифа вручную (maintenance). Действующая подписка пользователя заменяется.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class GrantPlanRequest {
    /** Длина комментария - как в БД */
    public static final int MAX_COMMENT_LENGTH = 500;

    /** id тарифа из конфигурации; не тариф по умолчанию */
    @NotBlank(message = "plan is required")
    private String plan;
    /** Последний день включительно (по Москве); не задан - бессрочно */
    private LocalDate validUntil;
    /** Зачем выдан: для истории */
    @Size(max = MAX_COMMENT_LENGTH, message = "comment must be at most 500 characters")
    private String comment;
}
