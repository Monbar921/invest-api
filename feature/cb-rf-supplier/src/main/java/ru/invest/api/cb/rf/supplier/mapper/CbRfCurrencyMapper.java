package ru.invest.api.cb.rf.supplier.mapper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import ru.invest.api.cb.rf.supplier.model.CurrencyDto;
import ru.invest.api.cb.rf.supplier.model.CurrencyElementDto;
import ru.invest.api.common.model.CurrencyModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * ЦБ отдаёт, сколько рублей (Value) стоят Nominal единиц валюты: например, USD 1 = 81,12 или JPY 100 = 55,34.
 * CurrencyModel.rate - множитель для пересчёта суммы из base в target, поэтому из каждой валюты строятся обе пары:
 * CHARCODE -> RUB (Value / Nominal) и RUB -> CHARCODE (Nominal / Value).
 */
@Mapper
public interface CbRfCurrencyMapper {
    String RUB = "RUB";
    int RATE_SCALE = 10;

    default List<CurrencyModel> toModel(final CurrencyDto currencyDto) {
        if (currencyDto == null || CollectionUtils.isEmpty(currencyDto.getCurrencies())) {
            return Collections.emptyList();
        }

        return currencyDto.getCurrencies()
                .stream()
                .filter(Objects::nonNull)
                .flatMap(this::toModels)
                .toList();
    }

    private Stream<CurrencyModel> toModels(final CurrencyElementDto currency) {
        final String charCode = StringUtils.upperCase(currency.getCharCode());
        final BigDecimal rubles = currency.getRate();
        final BigDecimal nominal = currency.getNominal();

        if (StringUtils.isBlank(charCode) || ObjectUtils.anyNull(rubles, nominal)
                || rubles.signum() <= 0 || nominal.signum() <= 0) {
            return Stream.empty();
        }

        return Stream.of(
                new CurrencyModel(charCode, RUB, rubles.divide(nominal, RATE_SCALE, RoundingMode.HALF_UP)),
                new CurrencyModel(RUB, charCode, nominal.divide(rubles, RATE_SCALE, RoundingMode.HALF_UP))
        );
    }
}
