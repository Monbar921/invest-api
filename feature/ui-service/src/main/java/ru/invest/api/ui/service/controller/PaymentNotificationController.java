package ru.invest.api.ui.service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.payment.service.provider.InvalidNotificationException;
import ru.invest.api.payment.service.usecase.PaymentUseCase;

/**
 * Webhook платёжного провайдера. Без пользователя и сессии: gateway открывает
 * /api/payments/notifications/** → /internal/rest/payments/notifications/** без входа, подлинность - подпись в теле.
 * Ответ - тело, которого ждёт провайдер (у Т-Кассы - OK); неверная подпись - 400, ничего не меняется.
 */
@Slf4j
@RestController
@RequestMapping("/internal/rest/payments/notifications")
@RequiredArgsConstructor
public class PaymentNotificationController {
    private final PaymentUseCase paymentUseCase;

    @PostMapping(value = "/{provider}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> notification(@PathVariable final String provider,
                                               @RequestBody(required = false) final String body) {
        try {
            return ResponseEntity.ok(paymentUseCase.handleNotification(provider, body == null ? "" : body));
        } catch (final InvalidNotificationException e) {
            log.warn("Payment notification rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid notification");
        }
    }
}
