package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.invest.api.common.entity.PriceCandle;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PriceCandleRepository extends JpaRepository<PriceCandle, Long> {
    List<PriceCandle> findByUidAndCandleDateBetweenOrderByCandleDateAsc(String uid, LocalDate from, LocalDate to);

    /**
     * Последняя дневная свеча каждой облигации раньше даты: закрытие прошлого торгового дня для изменения за день.
     */
    @Query("""
            select c from PriceCandle c
            where c.uid in :uids and c.candleDate = (
                select max(c2.candleDate) from PriceCandle c2 where c2.uid = c.uid and c2.candleDate < :date)
            """)
    List<PriceCandle> findLastBefore(Collection<String> uids, LocalDate date);

    @Query("select max(c.candleDate) from PriceCandle c where c.uid = :uid")
    Optional<LocalDate> findLastCandleDate(String uid);

    /**
     * Свечи с даты from включительно - перед записью свежих: последняя сохранённая могла быть незавершённой.
     */
    @Modifying
    @Query("delete from PriceCandle c where c.uid = :uid and c.candleDate >= :from")
    int deleteFrom(String uid, LocalDate from);
}
