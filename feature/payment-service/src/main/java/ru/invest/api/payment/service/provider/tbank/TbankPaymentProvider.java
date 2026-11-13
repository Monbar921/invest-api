package ru.invest.api.payment.service.provider.tbank;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.invest.api.common.model.payment.PaymentStatus;
import ru.invest.api.payment.service.config.PaymentProperties;
import ru.invest.api.payment.service.provider.InvalidNotificationException;
import ru.invest.api.payment.service.provider.PaymentProvider;
import ru.invest.api.payment.service.provider.PaymentProviderException;
import ru.invest.api.payment.service.provider.model.ChargeCommand;
import ru.invest.api.payment.service.provider.model.CreatePaymentCommand;
import ru.invest.api.payment.service.provider.model.ProviderPayment;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Т-Касса (интернет-эквайринг Т-Банка), API v2. Одностадийная оплата: успех - CONFIRMED. Покупка - Init с
 * Recurrent=Y и CustomerKey: после оплаты webhook приносит RebillId сохранённой карты. Автопродление - Init без
 * страницы оплаты и Charge с RebillId. Webhook подписан тем же Token, что и наши запросы.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TbankPaymentProvider implements PaymentProvider {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };
    private static final int KOPECKS = 2;

    private final PaymentProperties properties;
    private final TbankClient client;
    private final JsonMapper jsonMapper;

    @Override
    public String id() {
        return PaymentProperties.TBANK;
    }

    @Override
    public ProviderPayment create(final CreatePaymentCommand command) {
        final Map<String, Object> request = new LinkedHashMap<>();
        request.put("Amount", kopecks(command.amount()));
        request.put("OrderId", command.orderId());
        request.put("Description", command.description());
        request.put("CustomerKey", command.customerKey());
        // сохранить карту для автопродления: RebillId придёт в webhook успешной оплаты
        request.put("Recurrent", "Y");
        request.put("PayType", "O");
        request.put("NotificationURL", command.notificationUrl());
        request.put("SuccessURL", command.successUrl());
        request.put("FailURL", command.failUrl());
        final Map<String, Object> response = call("Init", request);
        return toPayment(response, command.orderId());
    }

    @Override
    public ProviderPayment charge(final ChargeCommand command) {
        final Map<String, Object> init = new LinkedHashMap<>();
        init.put("Amount", kopecks(command.amount()));
        init.put("OrderId", command.orderId());
        init.put("Description", command.description());
        init.put("PayType", "O");
        init.put("NotificationURL", command.notificationUrl());
        final ProviderPayment created = toPayment(call("Init", init), command.orderId());

        final Map<String, Object> charge = new LinkedHashMap<>();
        charge.put("PaymentId", created.providerPaymentId());
        charge.put("RebillId", command.rebillId());
        final Map<String, Object> response;
        try {
            response = send("Charge", charge);
        } catch (final PaymentProviderException e) {
            // платёж создан, но итог списания неизвестен: придёт в webhook или по GetState
            log.warn("T-Bank Charge of order {} has no result yet: {}", command.orderId(), e.getMessage());
            return new ProviderPayment(command.orderId(), created.providerPaymentId(), PaymentStatus.PENDING,
                    created.providerStatus(), created.amount(), null, null, null, null);
        }
        final ProviderPayment result = toPayment(response, command.orderId());
        if (Boolean.TRUE.equals(response.get("Success"))) {
            return result;
        }
        // отказ в списании (нет денег, карта заблокирована): денег не списано, это неудачная попытка
        return new ProviderPayment(result.orderId(), created.providerPaymentId(), PaymentStatus.FAILED,
                result.providerStatus(), null, null, null, null,
                result.errorMessage() == null ? "Списание отклонено" : result.errorMessage());
    }

    @Override
    public ProviderPayment getState(final String providerPaymentId) {
        final Map<String, Object> request = new LinkedHashMap<>();
        request.put("PaymentId", providerPaymentId);
        return toPayment(call("GetState", request), null);
    }

    @Override
    public ProviderPayment parseNotification(final String body) {
        final Map<String, Object> notification;
        try {
            notification = jsonMapper.readValue(body, MAP);
        } catch (final JacksonException e) {
            throw new InvalidNotificationException("T-Bank notification is not JSON");
        }
        final String terminalKey = properties.getTbank().getTerminalKey();
        if (notification == null || terminalKey == null || !terminalKey.equals(notification.get("TerminalKey"))) {
            throw new InvalidNotificationException("T-Bank notification for another terminal");
        }
        if (!TbankSignature.verify(notification, properties.getTbank().getPassword())) {
            throw new InvalidNotificationException("T-Bank notification signature mismatch");
        }
        return toNotification(notification);
    }

    @Override
    public String notificationResponse() {
        return "OK";
    }

    /** Запрос с подписью; отказ провайдера (Success=false) - исключение */
    private Map<String, Object> call(final String method, final Map<String, Object> params) {
        final Map<String, Object> response = send(method, params);
        if (!Boolean.TRUE.equals(response.get("Success"))) {
            throw new PaymentProviderException("T-Bank %s refused: ErrorCode=%s, %s %s".formatted(method,
                    response.get("ErrorCode"), response.get("Message"), response.getOrDefault("Details", "")));
        }
        return response;
    }

    /** Запрос с подписью; ответ как есть, и с Success=false */
    private Map<String, Object> send(final String method, final Map<String, Object> params) {
        final Map<String, Object> request = new LinkedHashMap<>();
        request.put("TerminalKey", properties.getTbank().getTerminalKey());
        request.putAll(params);
        request.put(TbankSignature.TOKEN, TbankSignature.token(request, properties.getTbank().getPassword()));
        return client.call(method, request);
    }

    private ProviderPayment toPayment(final Map<String, Object> response, final String orderId) {
        final String status = string(response.get("Status"));
        return new ProviderPayment(
                response.get("OrderId") == null ? orderId : string(response.get("OrderId")),
                string(response.get("PaymentId")),
                TbankStatuses.toStatus(status),
                status,
                rubles(response.get("Amount")),
                string(response.get("PaymentURL")),
                null,
                null,
                errorMessage(response));
    }

    private ProviderPayment toNotification(final Map<String, Object> notification) {
        final String status = string(notification.get("Status"));
        // Success=false - отказ, даже если статус промежуточный
        final boolean success = Boolean.TRUE.equals(notification.get("Success"));
        final PaymentStatus mapped = success || TbankStatuses.isFailed(status)
                ? TbankStatuses.toStatus(status)
                : PaymentStatus.FAILED;
        return new ProviderPayment(
                string(notification.get("OrderId")),
                string(notification.get("PaymentId")),
                mapped,
                status,
                rubles(notification.get("Amount")),
                null,
                string(notification.get("RebillId")),
                string(notification.get("Pan")),
                errorMessage(notification));
    }

    private static String errorMessage(final Map<String, Object> response) {
        final String code = string(response.get("ErrorCode"));
        if (code == null || "0".equals(code)) {
            return null;
        }
        final String message = string(response.get("Message"));
        return message == null ? "Код ошибки " + code : message;
    }

    private static long kopecks(final BigDecimal rubles) {
        return rubles.movePointRight(KOPECKS).longValueExact();
    }

    private static BigDecimal rubles(final Object kopecks) {
        return kopecks == null ? null : new BigDecimal(String.valueOf(kopecks)).movePointLeft(KOPECKS);
    }

    private static String string(final Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
