package ru.invest.api.ui.service.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.dto.bond.news.MarketOverviewDto;
import ru.invest.api.dto.bond.news.NewsDto;
import ru.invest.api.dto.bond.news.NewsSourceDto;
import ru.invest.api.dto.page.PageDto;
import ru.invest.api.news.supplier.usecase.MarketOverviewUseCase;
import ru.invest.api.news.supplier.usecase.NewsUseCase;
import ru.invest.api.ui.service.mapper.NewsDtoMapper;

import java.util.Set;

/**
 * Лента новостей. Пути под /bonds: gateway пропускает наружу только /api/bonds/**.
 */
@Validated
@RestController
@RequestMapping("/internal/rest/bonds")
@RequiredArgsConstructor
public class NewsController {
    private static final String DEFAULT_PAGE_SIZE = "20";
    private static final long MAX_PAGE_SIZE = 100;

    private final NewsUseCase newsUseCase;
    private final MarketOverviewUseCase marketOverviewUseCase;
    private final NewsDtoMapper newsDtoMapper;

    /**
     * Общая лента, новые первыми.
     *
     * @param source источники через запятую (CBR_PRESS, CBR_EVENT, MOEX); не задан - все
     * @param size   размер страницы (1..100), по умолчанию 20
     */
    @GetMapping("/news")
    public PageDto<NewsDto> getFeed(
            @RequestParam(required = false) final Set<NewsSourceDto> source,
            @RequestParam(defaultValue = "0") @PositiveOrZero(message = "page must not be negative") final int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Positive(message = "size must be a positive number")
            @Max(value = MAX_PAGE_SIZE, message = "size must not exceed 100") final int size) {
        return newsDtoMapper.toDto(newsUseCase.getFeed(newsDtoMapper.toSources(source), PageRequest.of(page, size)));
    }

    /**
     * Новости облигации: упомянут её ISIN или новость относится к её эмитенту.
     */
    @GetMapping("/{ticker}/news")
    public PageDto<NewsDto> getByTicker(
            @PathVariable final String ticker,
            @RequestParam(defaultValue = "0") @PositiveOrZero(message = "page must not be negative") final int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Positive(message = "size must be a positive number")
            @Max(value = MAX_PAGE_SIZE, message = "size must not exceed 100") final int size) {
        return newsDtoMapper.toDto(newsUseCase.getByTicker(ticker, PageRequest.of(page, size)));
    }

    /**
     * Ключевая ставка ЦБ и курсы доллара, евро и юаня с изменением к прошлому курсу.
     */
    @GetMapping("/market-overview")
    public MarketOverviewDto getMarketOverview() {
        return newsDtoMapper.toOverviewDto(marketOverviewUseCase.getOverview());
    }
}
