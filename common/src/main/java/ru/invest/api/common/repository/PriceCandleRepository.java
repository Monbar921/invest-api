package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.invest.api.common.entity.PriceCandle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    /**
     * Сумма объёма и объёма × цена закрытия (% номинала) по облигациям за период - для ликвидности.
     */
    @Query("""
            select c.uid as uid, sum(c.volume) as volume, sum(c.volume * c.close) as volumeByPrice
            from PriceCandle c
            where c.uid in :uids and c.candleDate >= :from and c.candleDate <= :to
            group by c.uid
            """)
    List<CandleVolume> sumVolumes(Collection<String> uids, LocalDate from, LocalDate to);

    /**
     * Торговых дней биржи за период: дни, в которые есть свеча хоть у одной облигации.
     */
    @Query("select count(distinct c.candleDate) from PriceCandle c where c.candleDate >= :from and c.candleDate <= :to")
    long countTradingDays(LocalDate from, LocalDate to);

    /**
     * Облигации, у которых загружена хоть одна свеча: у остальных объём неизвестен, а не нулевой.
     */
    @Query("select b.uid from Bond b where b.uid in :uids and exists (select 1 from PriceCandle c where c.uid = b.uid)")
    Set<String> findUidsWithCandles(Collection<String> uids);

    interface CandleVolume {
        String getUid();

        Long getVolume();

        BigDecimal getVolumeByPrice();
    }
}
