package ru.invest.api.tinkoff.supplier.service.impl;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CurrencyModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.currency.service.usecase.CurrencyPriceUseCase;
import ru.invest.api.tinkoff.supplier.service.PriceConversionService;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static ru.invest.api.tinkoff.supplier.constants.Constants.RU_CURRENCIES;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceConversionServiceImpl implements PriceConversionService {
    private static final String RUB = "RUB";

    private final CurrencyPriceUseCase currencyPriceUseCase;

    @Override
    public void fillCurrentInRub(final Collection<BondModel> bonds) {
        if (CollectionUtils.isEmpty(bonds)) {
            return;
        }

        // курс на валюту запрашивается один раз, в том числе неудачно - иначе при недоступном ЦБ
        // ушёл бы отдельный запрос на каждую иностранную облигацию
        final Map<String, Optional<BigDecimal>> rubRates = new HashMap<>();

        bonds.stream()
                .filter(Objects::nonNull)
                .map(BondModel::getPrice)
                .filter(Objects::nonNull)
                .forEach(price -> fillCurrentInRub(price, rubRates));
    }

    private void fillCurrentInRub(final PriceModel price, final Map<String, Optional<BigDecimal>> rubRates) {
        final MoneyModel current = price.getCurrent();
        if (current == null || current.getQuantity() == null || StringUtils.isBlank(current.getCurrency())) {
            return;
        }

        rubRates.computeIfAbsent(current.getCurrency().toUpperCase(), currency -> Optional.ofNullable(getRubRate(currency)))
                .ifPresent(rate -> price.setCurrentInRub(current.getQuantity().multiply(rate)));
    }

    private BigDecimal getRubRate(final String currency) {
        if (RU_CURRENCIES.contains(currency)) {
            return BigDecimal.ONE;
        }

        try {
            return Optional.ofNullable(currencyPriceUseCase.calculateAmount(currency, RUB, BigDecimal.ONE))
                    .map(CurrencyModel::getRate)
                    .orElse(null);
        } catch (final FeignException e) {
            log.warn("Failed to get {} to RUB rate, bonds in {} are treated as without price: {}", currency, currency, e.getMessage());
            return null;
        }
    }
}
