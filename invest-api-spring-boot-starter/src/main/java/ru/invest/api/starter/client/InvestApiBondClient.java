package ru.invest.api.starter.client;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import ru.invest.api.dto.bond.BondDto;
import ru.invest.api.dto.page.PageDto;
import ru.invest.api.dto.request.bond.BondParametersRequest;

import java.util.List;

/**
 * Клиент к BondController. Базовый путь /internal/rest задаётся в InvestApiBondClientAutoConfiguration.
 * <p>
 * Feign не отправляет null в теле запроса, поэтому bondParametersRequest в методах с телом обязателен;
 * для запроса без фильтров и сортировок есть перегрузки только с batchLimit.
 */
public interface InvestApiBondClient {
    /**
     * Все облигации.
     *
     * @param batchLimit            максимальное количество облигаций в ответе (>0), null - по умолчанию 100
     * @param bondParametersRequest поля и направления сортировки, фильтры по цене, риску и ОФЗ
     */
    @PostMapping("/bonds/all")
    List<BondDto> getAll(@RequestParam(value = "batchLimit", required = false) Integer batchLimit,
                         @RequestBody BondParametersRequest bondParametersRequest);

    /**
     * Все облигации без фильтров, с сортировкой по умолчанию (по текущей цене в рублях по возрастанию).
     */
    default List<BondDto> getAll(final Integer batchLimit) {
        return getAll(batchLimit, new BondParametersRequest());
    }

    /**
     * Облигации в иностранной валюте (ни текущая цена, ни номинал не в RUB).
     */
    @PostMapping("/bonds/foreign")
    List<BondDto> getForeign(@RequestParam(value = "batchLimit", required = false) Integer batchLimit,
                             @RequestBody BondParametersRequest bondParametersRequest);

    /**
     * Облигации в иностранной валюте без фильтров, с сортировкой по умолчанию.
     */
    default List<BondDto> getForeign(final Integer batchLimit) {
        return getForeign(batchLimit, new BondParametersRequest());
    }

    /**
     * Рублёвые облигации (текущая цена или номинал в RUB).
     */
    @PostMapping("/bonds/local")
    List<BondDto> getLocal(@RequestParam(value = "batchLimit", required = false) Integer batchLimit,
                           @RequestBody BondParametersRequest bondParametersRequest);

    /**
     * Рублёвые облигации без фильтров, с сортировкой по умолчанию.
     */
    default List<BondDto> getLocal(final Integer batchLimit) {
        return getLocal(batchLimit, new BondParametersRequest());
    }

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
}
