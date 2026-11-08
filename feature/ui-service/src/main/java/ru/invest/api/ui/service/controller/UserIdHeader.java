package ru.invest.api.ui.service.controller;

import ru.invest.api.common.exception.GeneralUnauthorizedException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;

/**
 * Пользователь эндпоинтов /alerts/**: sub из Keycloak, его ставит invest-gateway (UserIdHeaderFilter) и затирает
 * такой же заголовок из браузера. Своей проверки у invest-api нет - он доступен только через gateway.
 */
public final class UserIdHeader {
    public static final String NAME = "X-User-Id";

    private UserIdHeader() {
    }

    /**
     * @throws GeneralUnauthorizedException заголовка нет: запрос пришёл не через gateway (401)
     */
    public static String require(final String userId) {
        if (userId == null || userId.isBlank()) {
            throw new GeneralUnauthorizedException(ExceptionErrorCode.UNAUTHORIZED,
                    "Header %s is required: call through the gateway".formatted(NAME));
        }
        return userId;
    }
}
