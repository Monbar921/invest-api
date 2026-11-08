package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.invest.api.common.entity.CurrencyRate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, Long> {
    Optional<CurrencyRate> findByRateDateAndCurrency(LocalDate rateDate, String currency);

    List<CurrencyRate> findByRateDate(LocalDate rateDate);
}
