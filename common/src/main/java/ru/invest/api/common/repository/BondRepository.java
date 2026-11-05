package ru.invest.api.common.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.invest.api.common.entity.Bond;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BondRepository extends JpaRepository<Bond, Long> {
    @Query("""
            select b from Bond b
            left join fetch b.coupon c
            where c.isFixed = false
              or c.id is null
              or c.couponData is empty
            """)
    Slice<Bond> findByNotFixedCouponAndEmptyCoupons(Pageable pageable);

    /**
     * Дата последнего загруженного купона облигаций с фиксированным купоном - чтобы перезагрузить график,
     * который обрывается раньше погашения.
     */
    @Query("""
            select b.uid as uid, b.ticker as ticker, b.maturityDate as maturityDate, max(cd.paymentDate) as lastPaymentDate
            from Bond b
            join b.coupon c
            join c.couponData cd
            where c.isFixed = true
              and b.maturityDate > :now
            group by b.uid, b.ticker, b.maturityDate
            """)
    List<CouponScheduleEnd> findFixedCouponScheduleEnds(LocalDateTime now);

    /**
     * Все облигации сразу с ценой и купоном: обратные OneToOne-связи Hibernate иначе грузит
     * отдельным запросом на каждую облигацию.
     */
    @Query("""
            select b from Bond b
            left join fetch b.price
            left join fetch b.coupon
            """)
    List<Bond> findAllWithPriceAndCoupon();

    List<Bond> findByUidIn(Set<String> tickers);

    @Query("select b.isin from Bond b where b.isin is not null")
    Set<String> findAllIsins();

    List<Bond> findByIsinIn(Collection<String> isins);

    /**
     * Облигации с амортизацией - для синхронизации графика частичных погашений.
     */
    @Query("""
            select b from Bond b
            left join fetch b.price
            left join fetch b.coupon
            where b.amortizationFlag = true
            """)
    List<Bond> findAmortizing();

    @Query("""
            select distinct b from Bond b
            left join fetch b.price
            left join fetch b.coupon
            left join fetch b.amortizations
            where b.uid in :uids
            """)
    List<Bond> findWithAmortizationsByUidIn(Set<String> uids);

    Optional<Bond> findByUid(String uid);

    Optional<Bond> findByTicker(String ticker);

    /**
     * Облигация с ценой, купоном и графиком выплат - для карточки облигации.
     */
    @Query("""
            select b from Bond b
            left join fetch b.price
            left join fetch b.coupon c
            left join fetch c.couponData
            where b.ticker = :ticker
            """)
    Optional<Bond> findWithCouponDataByTicker(String ticker);
}
