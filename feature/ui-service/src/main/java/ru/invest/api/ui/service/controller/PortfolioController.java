package ru.invest.api.ui.service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.dto.portfolio.PortfolioDto;
import ru.invest.api.dto.portfolio.PortfolioPositionDto;
import ru.invest.api.dto.portfolio.PortfolioPositionRequest;
import ru.invest.api.portfolio.service.usecase.PortfolioUseCase;
import ru.invest.api.ui.service.mapper.PortfolioDtoMapper;

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

    @DeleteMapping("/positions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
                       @PathVariable final Long id) {
        portfolioUseCase.delete(UserIdHeader.require(userId), id);
    }
}
