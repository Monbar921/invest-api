package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.invest.api.common.entity.PriceCandle;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PriceCandleRepository extends JpaRepository<PriceCandle, Long> {
    List<PriceCandle> findByUidAndCandleDateBetweenOrderByCandleDateAsc(String uid, LocalDate from, LocalDate to);

    @Query("select max(c.candleDate) from PriceCandle c where c.uid = :uid")
    Optional<LocalDate> findLastCandleDate(String uid);

    /**
     * Свечи с даты from включительно - перед записью свежих: последняя сохранённая могла быть незавершённой.
     */
    @Modifying
    @Query("delete from PriceCandle c where c.uid = :uid and c.candleDate >= :from")
    int deleteFrom(String uid, LocalDate from);
}
