package ru.invest.api.dto.account;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Покупка тарифа.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CreatePaymentRequest {
    /** id тарифа с ценой */
    @NotBlank(message = "plan is required")
    private String plan;
}
