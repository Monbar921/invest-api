package ru.invest.api.ui.service.controller.maintenance;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.account.service.usecase.AccountPlanUseCase;
import ru.invest.api.dto.account.AccountPlanDto;
import ru.invest.api.dto.account.GrantPlanRequest;
import ru.invest.api.payment.service.usecase.PaymentUseCase;
import ru.invest.api.ui.service.mapper.AccountDtoMapper;

import static org.springframework.http.HttpHeaders.FROM;

/**
 * Выдача и отзыв тарифа вручную - для тестов, первых пользователей и поддержки. userId - sub Keycloak.
 * Через gateway недоступно, как и остальные maintenance-эндпоинты.
 */
@RestController
@RequestMapping("/internal/rest/maintenance/account/{userId}/plan")
@RequiredArgsConstructor
public class AccountPlanMaintenanceController {
    private final AccountPlanUseCase accountPlanUseCase;
    private final PaymentUseCase paymentUseCase;
    private final AccountDtoMapper accountDtoMapper;

    /** То же, что видит пользователь на странице «Тариф» */
    @GetMapping
    public AccountPlanDto getPlan(@PathVariable final String userId) {
        return accountDtoMapper.toDto(accountPlanUseCase.getPlan(userId), paymentUseCase.info(userId));
    }

    /**
     * Выдать тариф с текущего момента; действующая подписка заменяется, автопродление отключается. Неизвестный
     * тариф, тариф по умолчанию или срок в прошлом - 422.
     */
    @PutMapping
    public AccountPlanDto grant(@RequestHeader(FROM) final String user, @PathVariable final String userId,
                                @RequestBody @Valid final GrantPlanRequest request) {
        return accountDtoMapper.toDto(accountPlanUseCase.grant(userId, accountDtoMapper.toModel(request), user),
                paymentUseCase.info(userId));
    }

    /**
     * Отозвать подписку: пользователь сразу на тарифе по умолчанию, автопродление отключается. Подписки не было -
     * тоже 204.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@RequestHeader(FROM) final String user, @PathVariable final String userId) {
        accountPlanUseCase.revoke(userId, user);
    }
}
