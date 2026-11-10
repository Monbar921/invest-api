package ru.invest.api.ui.service.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.dto.bond.BondDto;
import ru.invest.api.dto.bond.BondSearchItemDto;
import ru.invest.api.dto.bond.calculation.ProfitCalculationDto;
import ru.invest.api.dto.bond.calculation.ProfitCalculationRequest;
import ru.invest.api.dto.bond.candle.CandleDto;
import ru.invest.api.dto.bond.candle.CandleIntervalDto;
import ru.invest.api.dto.bond.event.BondEventDto;
import ru.invest.api.dto.page.PageDto;
import ru.invest.api.dto.request.bond.BondParametersRequest;
import ru.invest.api.ui.service.mapper.BondDtoMapper;
import ru.invest.api.news.supplier.usecase.IssuerUseCase;
import ru.invest.api.ui.service.mapper.BondParametersRequestMapper;
import ru.invest.api.ui.service.mapper.IssuerDtoMapper;
import ru.invest.api.ui.service.mapper.NewsDtoMapper;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/internal/rest/bonds")
@RequiredArgsConstructor
public class BondController {
    private static final String DEFAULT_PAGE_SIZE = "50";
    private static final long MAX_PAGE_SIZE = 500;
    private static final String DEFAULT_SEARCH_LIMIT = "20";
    private static final long MAX_SEARCH_LIMIT = 50;

    private final BondUseCase bondUseCase;
    private final BondParametersRequestMapper bondParametersRequestMapper;
    private final BondDtoMapper bondDtoMapper;
    private final NewsDtoMapper newsDtoMapper;
    private final IssuerUseCase issuerUseCase;
    private final IssuerDtoMapper issuerDtoMapper;

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
     * Облигация с ценой и купоном вместе с графиком выплат и эмитентом (для ссылки на его страницу).
     */
    @GetMapping("/{ticker}")
    public BondDto getByTicker(@PathVariable final String ticker) {
        final BondModel bond = bondUseCase.getByTicker(ticker);
        return bondDtoMapper.toDto(bond)
                .setIssuer(issuerUseCase.findByIsin(bond.getIsin()).map(issuerDtoMapper::toShortDto).orElse(null));
    }

    /**
     * Нестрогий поиск облигаций по тикеру, ISIN или названию - для подсказок при вводе.
     *
     * @param query строка поиска; короче двух символов - пустой список
     * @param limit сколько облигаций вернуть (1..50)
     */
    @GetMapping("/search")
    public List<BondSearchItemDto> search(
            @RequestParam final String query,
            @RequestParam(defaultValue = DEFAULT_SEARCH_LIMIT) @Positive(message = "limit must be a positive number")
            @Max(value = MAX_SEARCH_LIMIT, message = "limit must not exceed 50") final int limit) {
        return bondDtoMapper.toSearchItems(bondUseCase.search(query, limit));
    }

    /**
     * Доход по облигации за период владения: покупка, купоны, погашения номинала, продажа, НДФЛ.
     */
    @PostMapping("/{ticker}/calculation")
    public ProfitCalculationDto calculateProfit(@PathVariable final String ticker,
                                                @RequestBody(required = false) final ProfitCalculationRequest request) {
        return bondDtoMapper.toCalculationDto(bondUseCase.calculateProfit(ticker, bondDtoMapper.toCalculationModel(request)));
    }

    /**
     * Свечи цены облигации в % от номинала для графика.
     *
     * @param interval DAY, WEEK или MONTH; по умолчанию DAY
     * @param from     первый день (YYYY-MM-DD), по умолчанию - вся история
     * @param to       последний день, по умолчанию - сегодня
     */
    @GetMapping("/{ticker}/candles")
    public List<CandleDto> getCandles(@PathVariable final String ticker,
                                      @RequestParam(defaultValue = "DAY") final CandleIntervalDto interval,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate to) {
        return bondDtoMapper.toCandleDto(bondUseCase.getCandles(ticker, bondDtoMapper.toCandleInterval(interval), from, to));
    }

    /**
     * События облигации из её графика выплат и дат: купоны (с изменением размера), амортизация, оферта, погашение -
     * за последние 3 месяца и все будущие, по возрастанию даты. Это не внешние новости: они - в /{ticker}/news.
     */
    @GetMapping("/{ticker}/events")
    public List<BondEventDto> getEvents(@PathVariable final String ticker) {
        return newsDtoMapper.toEventDto(bondUseCase.getEvents(ticker));
    }
}
