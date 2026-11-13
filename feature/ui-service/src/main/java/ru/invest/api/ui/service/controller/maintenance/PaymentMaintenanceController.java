package ru.invest.api.ui.service.controller.maintenance;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.dto.account.PaymentDto;
import ru.invest.api.payment.service.usecase.PaymentUseCase;
import ru.invest.api.payment.service.usecase.RenewalUseCase;
import ru.invest.api.ui.service.mapper.AccountDtoMapper;

import java.util.List;
import java.util.Map;

/**
 * Оплата для поддержки: платежи пользователя и запуск автопродления сейчас, не дожидаясь шедулера. userId - sub
 * Keycloak. Через gateway недоступно, как и остальные maintenance-эндпоинты.
 */
@RestController
@RequestMapping("/internal/rest/maintenance/payments")
@RequiredArgsConstructor
public class PaymentMaintenanceController {
    private final PaymentUseCase paymentUseCase;
    private final RenewalUseCase renewalUseCase;
    private final AccountDtoMapper accountDtoMapper;

    @GetMapping("/users/{userId}")
    public List<PaymentDto> payments(@PathVariable final String userId) {
        return paymentUseCase.history(userId).stream().map(accountDtoMapper::toDto).toList();
    }

    /**
     * Списать по автопродлениям, которым пора; в ответе - сколько обработано.
     */
    @PostMapping("/renewals")
    public Map<String, Integer> renew() {
        return Map.of("processed", renewalUseCase.renewDue());
    }
}
