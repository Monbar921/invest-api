package ru.invest.api.payment.service.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.stream.Stream;

/**
 * Оплата тарифов. Цены и сроки - у тарифов (ru.invest.api.plans). Пароль терминала - только из переменной окружения.
 * Включённая оплата без адресов или ключей терминала не даёт приложению стартовать.
 */
@Data
@Component
@Validated
@SuppressWarnings("checkstyle:MagicNumber")
@ConfigurationProperties(prefix = "ru.invest.api.payments")
public class PaymentProperties {
    /** Т-Касса (интернет-эквайринг Т-Банка) - пока единственный провайдер */
    public static final String TBANK = "tbank";

    /** false - купить тариф нельзя (UI показывает «Оплата пока недоступна»), автопродление не списывает */
    private boolean enabled;

    /** Провайдер: tbank */
    private String provider = TBANK;

    /** Публичный адрес webhook провайдера через gateway: https://host/api/payments/notifications/tbank */
    private String notificationUrl;

    /** Куда провайдер вернёт после оплаты; {paymentId} - наш id платежа: https://host/plan?payment={paymentId} */
    private String successUrl;

    /** Куда провайдер вернёт после отказа; {paymentId} - как в success-url */
    private String failUrl;

    /** Оферта (договор), с которой соглашается покупатель */
    private String offerUrl;

    /** Политика обработки персональных данных */
    private String privacyUrl;

    @Valid
    @NotNull
    private Renewal renewal = new Renewal();

    @Valid
    @NotNull
    private Tbank tbank = new Tbank();

    @AssertTrue(message = "при enabled нужны notification-url, success-url, fail-url, offer-url и privacy-url")
    public boolean isUrlsSet() {
        return !enabled || Stream.of(notificationUrl, successUrl, failUrl, offerUrl, privacyUrl)
                .allMatch(url -> url != null && !url.isBlank());
    }

    @AssertTrue(message = "provider: поддерживается только tbank; при enabled нужны tbank.terminal-key и tbank.password")
    public boolean isProviderValid() {
        return TBANK.equals(provider) && (!enabled || notBlank(tbank.getTerminalKey()) && notBlank(tbank.getPassword()));
    }

    private static boolean notBlank(final String value) {
        return value != null && !value.isBlank();
    }

    @Data
    public static class Renewal {
        /** false - автопродление не списывает (сохранённые карты остаются) */
        private boolean enabled = true;

        /** false - шедулер не создаётся (тесты вызывают RenewalUseCase сами) */
        private boolean scheduler = true;

        /** Как часто искать автопродления, которым пора списывать */
        @NotNull
        private Duration interval = Duration.ofHours(1);

        /** За сколько до конца подписки списывать */
        @NotNull
        private Duration chargeBefore = Duration.ofDays(1);

        /** Через сколько повторить неудачное списание */
        @NotNull
        private Duration retryInterval = Duration.ofDays(1);

        /** Сколько неудачных списаний подряд - и автопродление отключается */
        @Min(1)
        private int maxAttempts = 3;
    }

    @Data
    public static class Tbank {
        /** API v2 Т-Кассы; тестовый терминал (TerminalKey с суффиксом DEMO) работает с тем же адресом */
        @NotNull
        private String apiUrl = "https://securepay.tinkoff.ru/v2";

        private String terminalKey;

        /** Пароль терминала: им подписываются запросы и проверяется подпись webhook */
        private String password;

        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(10);

        @NotNull
        private Duration readTimeout = Duration.ofSeconds(30);
    }
}
