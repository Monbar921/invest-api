package ru.invest.api.cb.rf.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import ru.invest.api.cb.rf.supplier.client.feign.CbRfClient;
import ru.invest.api.cb.rf.supplier.mapper.CbRfCurrencyMapper;
import ru.invest.api.cb.rf.supplier.usecase.CbRfCurrencyUseCase;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.CurrencyModel;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static ru.invest.api.common.constants.CacheConstants.CB_RF_ALL_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.CB_RF_ALL_CACHE_NAME;

@Component
@RequiredArgsConstructor
public class CbRfCurrencyUseCaseImpl implements CbRfCurrencyUseCase {
    private final static DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CbRfCurrencyMapper currencyMapper;

    private final CbRfClient cbRfClient;

    @Override
    @Cacheable(value = CB_RF_ALL_CACHE_NAME, cacheManager = CB_RF_ALL_CACHE_MANAGER)
    public List<CurrencyModel> getCurrencies(final LocalDate localDate) {
        if (localDate == null) {
            throw new GeneralUnprocessableEntityException(ExceptionErrorCode.EMPTY_DATE, "Provide date");
        }

        return currencyMapper.toModel(
                cbRfClient.getCurrencyRates(DATE_FORMAT.format(localDate))
        );
    }
}
