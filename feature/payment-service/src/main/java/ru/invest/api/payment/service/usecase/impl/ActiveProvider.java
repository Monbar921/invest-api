package ru.invest.api.payment.service.usecase.impl;

import org.springframework.stereotype.Component;
import ru.invest.api.payment.service.config.PaymentProperties;
import ru.invest.api.payment.service.provider.PaymentProvider;

import java.util.List;

/**
 * Провайдер из настройки ru.invest.api.payments.provider.
 */
@Component
class ActiveProvider {
    private final PaymentProvider provider;

    ActiveProvider(final PaymentProperties properties, final List<PaymentProvider> providers) {
        this.provider = providers.stream()
                .filter(candidate -> candidate.id().equals(properties.getProvider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Payment provider %s is not available"
                        .formatted(properties.getProvider())));
    }

    PaymentProvider get() {
        return provider;
    }
}
