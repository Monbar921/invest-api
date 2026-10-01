package ru.invest.api.ui.service.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.dto.bond.BondDto;
import ru.invest.api.dto.page.PageDto;
import ru.invest.api.dto.request.bond.BondParametersRequest;
import ru.invest.api.ui.service.mapper.BondDtoMapper;
import ru.invest.api.ui.service.mapper.BondParametersRequestMapper;

import java.util.List;

@Validated
@RestController
@RequestMapping("/internal/rest/bonds")
@RequiredArgsConstructor
public class BondController {
    private static final String DEFAULT_PAGE_SIZE = "50";
    private static final long MAX_PAGE_SIZE = 500;

    private final BondUseCase bondUseCase;
    private final BondParametersRequestMapper bondParametersRequestMapper;
    private final BondDtoMapper bondDtoMapper;

    /**
     * Возвращает список иностранных облигаций.
     *
     * @param batchLimit      максимальное количество облигаций в ответе (>0)
     * @param bondParametersRequest поля и направления сортировки, ограничения по цене
     */
    @PostMapping("/all")
    public List<BondDto> getAll(
            @RequestParam(required = false) @Positive(message = "batchLimit must be a positive number") final Integer batchLimit,
            @RequestBody(required = false) final BondParametersRequest bondParametersRequest) {
        return bondDtoMapper.toDto(
                bondUseCase.getAll(
                        bondParametersRequestMapper.toModel(batchLimit, bondParametersRequest)));
    }

    @PostMapping("/foreign")
    public List<BondDto> getForeign(
            @RequestParam(required = false) @Positive(message = "batchLimit must be a positive number") final Integer batchLimit,
            @RequestBody(required = false) final BondParametersRequest bondParametersRequest) {
        return bondDtoMapper.toDto(
                bondUseCase.getForeignCurrencyBonds(
                        bondParametersRequestMapper.toModel(batchLimit, bondParametersRequest)));
    }

    @PostMapping("/local")
    public List<BondDto> getLocal(
            @RequestParam(required = false) @Positive(message = "batchLimit must be a positive number") final Integer batchLimit,
            @RequestBody(required = false) final BondParametersRequest bondParametersRequest) {
        return bondDtoMapper.toDto(
                bondUseCase.getRubbleCurrencyBonds(
                        bondParametersRequestMapper.toModel(batchLimit, bondParametersRequest)));
    }

    /**
     * Страница всех облигаций.
     *
     * @param page                  номер страницы, начиная с 0
     * @param size                  размер страницы (1..500)
     * @param bondParametersRequest поля и направления сортировки, фильтры по цене, риску и ОФЗ
     */
    @PostMapping("/all/page")
    public PageDto<BondDto> getAllPage(
            @RequestParam(defaultValue = "0") @PositiveOrZero(message = "page must not be negative") final int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Positive(message = "size must be a positive number")
            @Max(value = MAX_PAGE_SIZE, message = "size must not exceed 500") final int size,
            @RequestBody(required = false) final BondParametersRequest bondParametersRequest) {
        return bondDtoMapper.toDto(
                bondUseCase.getAllPage(
                        bondParametersRequestMapper.toModel(null, bondParametersRequest), PageRequest.of(page, size)));
    }

    /**
     * Страница облигаций в иностранной валюте.
     */
    @PostMapping("/foreign/page")
    public PageDto<BondDto> getForeignPage(
            @RequestParam(defaultValue = "0") @PositiveOrZero(message = "page must not be negative") final int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Positive(message = "size must be a positive number")
            @Max(value = MAX_PAGE_SIZE, message = "size must not exceed 500") final int size,
            @RequestBody(required = false) final BondParametersRequest bondParametersRequest) {
        return bondDtoMapper.toDto(
                bondUseCase.getForeignCurrencyBondsPage(
                        bondParametersRequestMapper.toModel(null, bondParametersRequest), PageRequest.of(page, size)));
    }

    /**
     * Страница рублёвых облигаций.
     */
    @PostMapping("/local/page")
    public PageDto<BondDto> getLocalPage(
            @RequestParam(defaultValue = "0") @PositiveOrZero(message = "page must not be negative") final int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Positive(message = "size must be a positive number")
            @Max(value = MAX_PAGE_SIZE, message = "size must not exceed 500") final int size,
            @RequestBody(required = false) final BondParametersRequest bondParametersRequest) {
        return bondDtoMapper.toDto(
                bondUseCase.getRubbleCurrencyBondsPage(
                        bondParametersRequestMapper.toModel(null, bondParametersRequest), PageRequest.of(page, size)));
    }

    /**
     * Облигация с ценой и купоном вместе с графиком выплат.
     */
    @GetMapping("/{ticker}")
    public BondDto getByTicker(@PathVariable final String ticker) {
        return bondDtoMapper.toDto(bondUseCase.getByTicker(ticker));
    }
}
