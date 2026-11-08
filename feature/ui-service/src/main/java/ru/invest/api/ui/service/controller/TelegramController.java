package ru.invest.api.ui.service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.dto.telegram.TelegramLinkDto;
import ru.invest.api.dto.telegram.TelegramStatusDto;
import ru.invest.api.telegram.bot.usecase.TelegramLinkUseCase;
import ru.invest.api.ui.service.mapper.TelegramDtoMapper;

/**
 * Подключение Telegram для уведомлений. Пользователя (sub Keycloak) передаёт gateway в заголовке X-User-Id;
 * без него - 401. Наружу через gateway: /api/alerts/** → /internal/rest/alerts/**.
 */
@RestController
@RequestMapping("/internal/rest/alerts/telegram")
@RequiredArgsConstructor
public class TelegramController {
    private final TelegramLinkUseCase telegramLinkUseCase;
    private final TelegramDtoMapper telegramDtoMapper;

    /**
     * Включён ли бот, привязан ли чат, отвечает ли Telegram.
     */
    @GetMapping
    public TelegramStatusDto getStatus(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId) {
        return telegramDtoMapper.toDto(telegramLinkUseCase.getStatus(UserIdHeader.require(userId)));
    }

    /**
     * Одноразовая ссылка t.me/&lt;bot&gt;?start=&lt;токен&gt;, действует 10 минут; прежние ссылки перестают
     * действовать. Бот выключен - 422.
     */
    @PostMapping("/link-token")
    public TelegramLinkDto createLink(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId) {
        return telegramDtoMapper.toDto(telegramLinkUseCase.createLink(UserIdHeader.require(userId)));
    }

    /**
     * Отвязать чат; если чата не было - тоже 204.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId) {
        telegramLinkUseCase.unlinkByUser(UserIdHeader.require(userId));
    }
}
