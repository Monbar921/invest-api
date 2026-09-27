package ru.invest.api.starter.client;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import ru.invest.api.dto.bond.BondDto;
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
     * Все облигации без фильтров, с сортировкой по умолчанию (по текущей цене по возрастанию).
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
}
