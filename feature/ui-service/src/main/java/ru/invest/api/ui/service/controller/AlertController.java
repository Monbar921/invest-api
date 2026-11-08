package ru.invest.api.ui.service.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.alert.service.usecase.AlertUseCase;
import ru.invest.api.dto.alert.AlertCreateRequest;
import ru.invest.api.dto.alert.AlertDto;
import ru.invest.api.dto.alert.AlertUpdateRequest;
import ru.invest.api.dto.alert.NotificationDto;
import ru.invest.api.dto.page.PageDto;
import ru.invest.api.ui.service.mapper.AlertDtoMapper;

import java.util.List;

/**
 * Правила уведомлений пользователя и история отправленного. Пользователь - X-User-Id от gateway, без него 401;
 * чужое правило - 404. Наружу через gateway: /api/alerts/** → /internal/rest/alerts/**.
 */
@Validated
@RestController
@RequestMapping("/internal/rest/alerts")
@RequiredArgsConstructor
public class AlertController {
    private static final String DEFAULT_PAGE_SIZE = "20";
    private static final long MAX_PAGE_SIZE = 100;

    private final AlertUseCase alertUseCase;
    private final AlertDtoMapper alertDtoMapper;

    /**
     * Правила пользователя по облигациям.
     *
     * @param ticker только по этой облигации; не задан - все
     */
    @GetMapping
    public List<AlertDto> getAlerts(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
                                    @RequestParam(required = false) final String ticker) {
        return alertDtoMapper.toDto(alertUseCase.getAlerts(UserIdHeader.require(userId), ticker));
    }

    /**
     * Новое правило; если событие уже в окне - уведомление уходит сразу. Облигации нет - 404; такое правило уже есть
     * или их больше 50 - 422.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertDto create(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
                           @RequestBody @Valid final AlertCreateRequest request) {
        return alertDtoMapper.toDto(alertUseCase.create(UserIdHeader.require(userId), request.getTicker(),
                alertDtoMapper.toType(request.getType()), request.getDays()));
    }

    /**
     * Изменить дни и/или включить, выключить правило.
     */
    @PutMapping("/{id}")
    public AlertDto update(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
                           @PathVariable final Long id,
                           @RequestBody @Valid final AlertUpdateRequest request) {
        return alertDtoMapper.toDto(alertUseCase.update(UserIdHeader.require(userId), id, request.getDays(),
                request.getEnabled()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
                       @PathVariable final Long id) {
        alertUseCase.delete(UserIdHeader.require(userId), id);
    }

    /**
     * История уведомлений, новые первыми.
     */
    @GetMapping("/notifications")
    public PageDto<NotificationDto> getNotifications(
            @RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
            @RequestParam(defaultValue = "0") @PositiveOrZero(message = "page must not be negative") final int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Positive(message = "size must be a positive number")
            @Max(value = MAX_PAGE_SIZE, message = "size must not exceed 100") final int size) {
        return alertDtoMapper.toDto(alertUseCase.getNotifications(UserIdHeader.require(userId),
                PageRequest.of(page, size)));
    }
}
