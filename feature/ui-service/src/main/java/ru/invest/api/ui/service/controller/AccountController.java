package ru.invest.api.ui.service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.account.service.usecase.AccountPlanUseCase;
import ru.invest.api.dto.account.AccountPlanDto;
import ru.invest.api.dto.account.CreatePaymentRequest;
import ru.invest.api.dto.account.PaymentDto;
import ru.invest.api.payment.service.usecase.PaymentUseCase;
import ru.invest.api.ui.service.mapper.AccountDtoMapper;

import java.util.List;

/**
 * Тариф пользователя и его оплата. Пользователь - X-User-Id от gateway, без него 401. Наружу через gateway:
 * /api/account/** → /internal/rest/account/**.
 */
@RestController
@RequestMapping("/internal/rest/account")
@RequiredArgsConstructor
public class AccountController {
    private final AccountPlanUseCase accountPlanUseCase;
    private final PaymentUseCase paymentUseCase;
    private final AccountDtoMapper accountDtoMapper;

    /**
     * Действующий тариф, срок подписки, лимиты с использованием, все тарифы с ценами и оплата (автопродление).
     */
    @GetMapping("/plan")
    public AccountPlanDto getPlan(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId) {
        final String user = UserIdHeader.require(userId);
        return accountDtoMapper.toDto(accountPlanUseCase.getPlan(user), paymentUseCase.info(user));
    }

    /**
     * Купить тариф: в ответе confirmationUrl - страница оплаты провайдера. Оплата выключена, тариф неизвестен или не
     * продаётся - 422, провайдер не ответил - 502.
     */
    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDto purchase(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
                               @RequestBody @Valid final CreatePaymentRequest request) {
        return accountDtoMapper.toDto(paymentUseCase.purchase(UserIdHeader.require(userId), request.getPlan()));
    }

    /**
     * Платежи пользователя, новые первыми.
     */
    @GetMapping("/payments")
    public List<PaymentDto> payments(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId) {
        return paymentUseCase.history(UserIdHeader.require(userId)).stream().map(accountDtoMapper::toDto).toList();
    }

    /**
     * Платёж пользователя; ждёт оплаты - статус сначала уточняется у провайдера. Чужой или неизвестный - 404.
     */
    @GetMapping("/payments/{id}")
    public PaymentDto payment(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
                              @PathVariable final long id) {
        return accountDtoMapper.toDto(paymentUseCase.get(UserIdHeader.require(userId), id));
    }

    /**
     * Отключить автопродление: тариф действует до конца оплаченного срока. Не было - тоже 204.
     */
    @DeleteMapping("/auto-renewal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelAutoRenewal(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId) {
        paymentUseCase.cancelAutoRenewal(UserIdHeader.require(userId));
    }
}
