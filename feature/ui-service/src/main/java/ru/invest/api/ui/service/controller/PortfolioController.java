package ru.invest.api.ui.service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
import ru.invest.api.dto.portfolio.BrokerDto;
import ru.invest.api.dto.portfolio.CalendarLinkDto;
import ru.invest.api.dto.portfolio.PortfolioCalendarDto;
import ru.invest.api.dto.portfolio.PortfolioDto;
import ru.invest.api.dto.portfolio.PortfolioPositionDto;
import ru.invest.api.dto.portfolio.PortfolioPositionRequest;
import ru.invest.api.portfolio.service.usecase.PortfolioCalendarUseCase;
import ru.invest.api.portfolio.service.usecase.PortfolioUseCase;
import ru.invest.api.ui.service.mapper.PortfolioDtoMapper;

import java.time.LocalDate;
import java.util.List;

/**
 * Портфель облигаций пользователя: покупки и доход от цены покупки до текущей. Пользователь - X-User-Id от gateway,
 * без него 401; чужая позиция - 404. Наружу через gateway: /api/portfolio/** → /internal/rest/portfolio/**.
 */
@Validated
@RestController
@RequestMapping("/internal/rest/portfolio")
@RequiredArgsConstructor
public class PortfolioController {
    private final PortfolioUseCase portfolioUseCase;
    private final PortfolioCalendarUseCase portfolioCalendarUseCase;
    private final PortfolioDtoMapper portfolioDtoMapper;

    /**
     * Позиции с текущей ценой, стоимостью и доходом и итоги по валютам.
     */
    @GetMapping
    public PortfolioDto getPortfolio(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId) {
        return portfolioDtoMapper.toDto(portfolioUseCase.getPortfolio(UserIdHeader.require(userId)));
    }

    /**
     * Новая покупка. Облигации нет - 404; нет тикера, дата в будущем или позиций больше 200 - 422.
     */
    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioPositionDto create(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
                                       @RequestBody @Valid final PortfolioPositionRequest request) {
        return portfolioDtoMapper.toDto(portfolioUseCase.create(UserIdHeader.require(userId),
                portfolioDtoMapper.toModel(request)));
    }

    /**
     * Изменить количество, цену и дату покупки; тикер не меняется.
     */
    @PutMapping("/positions/{id}")
    public PortfolioPositionDto update(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
                                       @PathVariable final Long id,
                                       @RequestBody @Valid final PortfolioPositionRequest request) {
        return portfolioDtoMapper.toDto(portfolioUseCase.update(UserIdHeader.require(userId), id,
                portfolioDtoMapper.toModel(request)));
    }

    /**
     * Брокеры пользователя по имени - для выбора у покупки. Новый брокер появляется, когда его впервые указывают
     * у покупки, и остаётся, даже если покупок с ним больше нет.
     */
    @GetMapping("/brokers")
    public List<BrokerDto> getBrokers(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId) {
        return portfolioDtoMapper.toBrokerDto(portfolioUseCase.getBrokers(UserIdHeader.require(userId)));
    }

    @DeleteMapping("/positions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
                       @PathVariable final Long id) {
        portfolioUseCase.delete(UserIdHeader.require(userId), id);
    }

    /**
     * Календарь выплат: будущие купоны, амортизация, оферты и погашения по облигациям портфеля, суммы на количество,
     * итоги по месяцам и валютам. from не задан или в прошлом - с сегодня, to не задан - год от from; to раньше
     * from - 422.
     */
    @GetMapping("/calendar")
    public PortfolioCalendarDto getCalendar(
            @RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate to) {
        return portfolioDtoMapper.toCalendarDto(portfolioCalendarUseCase.getCalendar(UserIdHeader.require(userId), from, to));
    }

    /**
     * Есть ли личная ссылка на календарь (.ics); токен не отдаётся - хранится только его хеш.
     */
    @GetMapping("/calendar/link")
    public CalendarLinkDto getCalendarLink(
            @RequestHeader(value = UserIdHeader.NAME, required = false) final String userId) {
        return portfolioDtoMapper.toLinkDto(portfolioCalendarUseCase.getLink(UserIdHeader.require(userId)));
    }

    /**
     * Новая личная ссылка на календарь: токен в ответе, адрес /api/calendar/&lt;токен&gt;.ics строит UI. Прежняя
     * ссылка перестаёт работать.
     */
    @PostMapping("/calendar/link")
    public CalendarLinkDto createCalendarLink(
            @RequestHeader(value = UserIdHeader.NAME, required = false) final String userId) {
        return portfolioDtoMapper.toLinkDto(portfolioCalendarUseCase.createLink(UserIdHeader.require(userId)));
    }

    /**
     * Отключить ссылку на календарь; если её не было - тоже 204.
     */
    @DeleteMapping("/calendar/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCalendarLink(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId) {
        portfolioCalendarUseCase.deleteLink(UserIdHeader.require(userId));
    }
}
