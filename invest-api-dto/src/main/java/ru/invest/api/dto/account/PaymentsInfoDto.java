package ru.invest.api.dto.account;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Оплата на странице «Тариф».
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PaymentsInfoDto {
    /** Можно купить тариф; false - «Оплата пока недоступна» */
    private boolean enabled;
    /** Оферта (договор) - ссылка у кнопки оплаты */
    private String offerUrl;
    /** Политика обработки персональных данных */
    private String privacyUrl;
    /** Действующее автопродление; null - нет */
    private AutoRenewal autoRenewal;

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class AutoRenewal {
        /** id тарифа */
        private String plan;
        private String planTitle;
        /** Маска карты: 430000******0777 */
        private String cardPan;
        /** День следующего списания (по Москве) */
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate nextChargeDate;
        /** Сколько спишется, рубли; null - тариф больше не продаётся (списания не будет) */
        private BigDecimal amount;
    }
}
