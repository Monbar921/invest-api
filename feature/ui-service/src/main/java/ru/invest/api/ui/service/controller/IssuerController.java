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
import ru.invest.api.dto.bond.issuer.IssuerDto;
import ru.invest.api.dto.bond.news.NewsDto;
import ru.invest.api.dto.page.PageDto;
import ru.invest.api.news.supplier.usecase.IssuerUseCase;
import ru.invest.api.news.supplier.usecase.NewsUseCase;
import ru.invest.api.ui.service.mapper.IssuerDtoMapper;
import ru.invest.api.ui.service.mapper.NewsDtoMapper;

/**
 * Страница эмитента. Пути под /bonds: gateway пропускает наружу только /api/bonds/**, без входа - только чтение.
 */
@Validated
@RestController
@RequestMapping("/internal/rest/bonds/issuers")
@RequiredArgsConstructor
public class IssuerController {
    private static final String DEFAULT_PAGE_SIZE = "20";
    private static final long MAX_PAGE_SIZE = 100;

    private final IssuerUseCase issuerUseCase;
    private final NewsUseCase newsUseCase;
    private final IssuerDtoMapper issuerDtoMapper;
    private final NewsDtoMapper newsDtoMapper;

    /**
     * Эмитент: название, ИНН и выпуски из системы по дате погашения; неизвестный эмитент - 404.
     *
     * @param id id эмитента на Мосбирже
     */
    @GetMapping("/{id}")
    public IssuerDto getIssuer(@PathVariable final Long id) {
        return issuerDtoMapper.toDto(issuerUseCase.getIssuer(id));
    }

    /**
     * Новости эмитента, новые первыми: относятся к нему или упомянут любой его выпуск.
     */
    @GetMapping("/{id}/news")
    public PageDto<NewsDto> getNews(
            @PathVariable final Long id,
            @RequestParam(defaultValue = "0") @PositiveOrZero(message = "page must not be negative") final int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Positive(message = "size must be a positive number")
            @Max(value = MAX_PAGE_SIZE, message = "size must not exceed 100") final int size) {
        return newsDtoMapper.toDto(newsUseCase.getByIssuer(id, PageRequest.of(page, size)));
    }
}
