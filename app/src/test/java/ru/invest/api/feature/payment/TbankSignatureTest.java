package ru.invest.api.feature.payment;

import org.junit.jupiter.api.Test;
import ru.invest.api.common.model.payment.PaymentStatus;
import ru.invest.api.payment.service.provider.tbank.TbankStatuses;
import ru.invest.api.payment.service.provider.tbank.TbankSignature;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Подпись Т-Кассы и сведение статусов без Spring и БД.
 */
class TbankSignatureTest {
    private static final String PASSWORD = "usaf8fw8fsw21g";

    @Test
    void tokenMatchesProviderDocumentationExample() {
        // пример из документации Т-Кассы (раздел «Подпись запроса»)
        final Map<String, Object> request = new LinkedHashMap<>();
        request.put("TerminalKey", "MerchantTerminalKey");
        request.put("Amount", 19200);
        request.put("OrderId", "21090");
        request.put("Description", "Подарочная карта на 1000 рублей");

        assertThat(TbankSignature.token(request, PASSWORD))
                .isEqualTo("0024a00af7c350a3a67ca168ce06502aa72772456662e38696d48b56ee9c97d9");
    }

    @Test
    void nestedObjectsAndTokenAreNotSigned() {
        final Map<String, Object> request = new LinkedHashMap<>();
        request.put("TerminalKey", "MerchantTerminalKey");
        request.put("Amount", 19200);
        request.put("OrderId", "21090");
        request.put("Description", "Подарочная карта на 1000 рублей");
        request.put("Receipt", Map.of("Email", "a@test"));
        request.put("Items", List.of(1, 2));
        request.put("Token", "anything");

        assertThat(TbankSignature.token(request, PASSWORD))
                .isEqualTo("0024a00af7c350a3a67ca168ce06502aa72772456662e38696d48b56ee9c97d9");
    }

    @Test
    void notificationIsVerified() {
        final Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("TerminalKey", "MerchantTerminalKey");
        notification.put("OrderId", "21090");
        // в webhook булево и числа - не строки: подписываются как true / 19200
        notification.put("Success", true);
        notification.put("Status", "CONFIRMED");
        notification.put("PaymentId", 13660);
        notification.put("Amount", 19200);
        notification.put("Token", TbankSignature.token(notification, PASSWORD));

        assertThat(TbankSignature.verify(notification, PASSWORD)).isTrue();
        assertThat(TbankSignature.verify(notification, "other")).isFalse();
        // токен в верхнем регистре тоже принимается
        notification.put("Token", ((String) notification.get("Token")).toUpperCase());
        assertThat(TbankSignature.verify(notification, PASSWORD)).isTrue();
        notification.put("Amount", 100);
        assertThat(TbankSignature.verify(notification, PASSWORD)).isFalse();
        notification.remove("Token");
        assertThat(TbankSignature.verify(notification, PASSWORD)).isFalse();
    }

    @Test
    void statusesAreMapped() {
        assertThat(TbankStatuses.toStatus("CONFIRMED")).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(TbankStatuses.toStatus("REJECTED")).isEqualTo(PaymentStatus.FAILED);
        assertThat(TbankStatuses.toStatus("DEADLINE_EXPIRED")).isEqualTo(PaymentStatus.FAILED);
        assertThat(TbankStatuses.toStatus("CANCELED")).isEqualTo(PaymentStatus.FAILED);
        assertThat(TbankStatuses.toStatus("REFUNDED")).isEqualTo(PaymentStatus.REFUNDED);
        // частичный возврат тариф не отзывает
        assertThat(TbankStatuses.toStatus("PARTIAL_REFUNDED")).isEqualTo(PaymentStatus.PENDING);
        assertThat(TbankStatuses.toStatus("AUTHORIZED")).isEqualTo(PaymentStatus.PENDING);
        assertThat(TbankStatuses.toStatus("NEW")).isEqualTo(PaymentStatus.PENDING);
        assertThat(TbankStatuses.toStatus(null)).isEqualTo(PaymentStatus.PENDING);
    }
}
