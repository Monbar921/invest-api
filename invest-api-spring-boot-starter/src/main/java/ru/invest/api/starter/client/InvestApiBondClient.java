package ru.invest.api.starter.client;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import ru.invest.api.dto.bond.BondDto;
import ru.invest.api.dto.bond.BondSearchItemDto;
import ru.invest.api.dto.bond.calculation.ProfitCalculationDto;
import ru.invest.api.dto.bond.calculation.ProfitCalculationRequest;
import ru.invest.api.dto.bond.candle.CandleDto;
import ru.invest.api.dto.bond.candle.CandleIntervalDto;
import ru.invest.api.dto.page.PageDto;
import ru.invest.api.dto.request.bond.BondParametersRequest;

import java.time.LocalDate;
import java.util.List;

/**
 * Клиент к BondController. Базовый путь /internal/rest задаётся в InvestApiBondClientAutoConfiguration.
 * <p>
 * Feign не отправляет null в теле запроса, поэтому bondParametersRequest в методах с телом обязателен:
 * для запроса без фильтров и сортировок передаётся пустой BondParametersRequest.
 */
public interface InvestApiBondClient {
    /**
     * Страница всех облигаций.
     *
     * @param page                  номер страницы, начиная с 0, null - первая
     * @param size                  размер страницы (1..500), null - 50
     * @param bondParametersRequest поля и направления сортировки, фильтры по цене, риску и ОФЗ
     */
    @PostMapping("/bonds/all/page")
    PageDto<BondDto> getAllPage(@RequestParam(value = "page", required = false) Integer page,
                                @RequestParam(value = "size", required = false) Integer size,
                                @RequestBody BondParametersRequest bondParametersRequest);

    /**
     * Страница облигаций в иностранной валюте.
     */
    @PostMapping("/bonds/foreign/page")
    PageDto<BondDto> getForeignPage(@RequestParam(value = "page", required = false) Integer page,
                                    @RequestParam(value = "size", required = false) Integer size,
                                    @RequestBody BondParametersRequest bondParametersRequest);

    /**
     * Страница рублёвых облигаций.
     */
    @PostMapping("/bonds/local/page")
    PageDto<BondDto> getLocalPage(@RequestParam(value = "page", required = false) Integer page,
                                  @RequestParam(value = "size", required = false) Integer size,
                                  @RequestBody BondParametersRequest bondParametersRequest);

    /**
     * Облигация с ценой и купоном вместе с графиком выплат.
     */
    @GetMapping("/bonds/{ticker}")
    BondDto getByTicker(@PathVariable("ticker") String ticker);

    /**
     * Нестрогий поиск облигаций по тикеру, ISIN или названию.
     *
     * @param limit сколько облигаций вернуть (1..50), null - 20
     */
    @GetMapping("/bonds/search")
    List<BondSearchItemDto> search(@RequestParam("query") String query,
                                   @RequestParam(value = "limit", required = false) Integer limit);

    /**
     * Доход по облигации за период владения.
     */
    @PostMapping("/bonds/{ticker}/calculation")
    ProfitCalculationDto calculateProfit(@PathVariable("ticker") String ticker, @RequestBody ProfitCalculationRequest request);

    /**
     * Свечи цены облигации в % от номинала.
     *
     * @param interval null - DAY; from, to - YYYY-MM-DD, null - вся история и сегодня
     */
    @GetMapping("/bonds/{ticker}/candles")
    List<CandleDto> getCandles(@PathVariable("ticker") String ticker,
                               @RequestParam(value = "interval", required = false) CandleIntervalDto interval,
                               @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);
}
