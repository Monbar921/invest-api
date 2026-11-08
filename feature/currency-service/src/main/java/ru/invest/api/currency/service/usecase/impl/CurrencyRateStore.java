package ru.invest.api.currency.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.entity.CurrencyRate;
import ru.invest.api.common.model.CurrencyModel;
import ru.invest.api.common.repository.CurrencyRateRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сохранение курсов ЦБ на дату - отдельным бином, в своей транзакции: курс запрашивают и из read-only транзакций
 * (чтение портфеля), а в них вставка запрещена. Своя транзакция ещё и не даёт ошибке вставки (параллельный запрос
 * уже сохранил ту же дату) испортить транзакцию вызывающего.
 */
@Component
@RequiredArgsConstructor
class CurrencyRateStore {
    private final CurrencyRateRepository repository;

    /**
     * @param rates курсы к рублю (base - валюта, rate - рублей за единицу)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void storeMissing(final LocalDate date, final List<CurrencyModel> rates) {
        final Set<String> stored = repository.findByRateDate(date).stream()
                .map(CurrencyRate::getCurrency)
                .collect(Collectors.toSet());
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final List<CurrencyRate> missing = rates.stream()
                .filter(rate -> !stored.contains(rate.getBase().toUpperCase()))
                .map(rate -> new CurrencyRate()
                        .setRateDate(date)
                        .setCurrency(rate.getBase().toUpperCase())
                        .setRate(rate.getRate())
                        .setSource(CurrencyRate.SOURCE_CB_RF)
                        .setCreatedAt(now))
                .toList();
        if (!missing.isEmpty()) {
            repository.saveAll(missing);
        }
    }
}
