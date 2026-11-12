package ru.invest.api.ui.service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.account.service.usecase.AccountPlanUseCase;
import ru.invest.api.dto.account.AccountPlanDto;
import ru.invest.api.ui.service.mapper.AccountDtoMapper;

/**
 * Тариф пользователя. Пользователь - X-User-Id от gateway, без него 401. Наружу через gateway:
 * /api/account/** → /internal/rest/account/**.
 */
@RestController
@RequestMapping("/internal/rest/account")
@RequiredArgsConstructor
public class AccountController {
    private final AccountPlanUseCase accountPlanUseCase;
    private final AccountDtoMapper accountDtoMapper;

    /**
     * Действующий тариф, срок подписки, лимиты с использованием и все тарифы для сравнения.
     */
    @GetMapping("/plan")
    public AccountPlanDto getPlan(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId) {
        return accountDtoMapper.toDto(accountPlanUseCase.getPlan(UserIdHeader.require(userId)));
    }
}
