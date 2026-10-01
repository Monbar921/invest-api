package ru.invest.api.common.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.invest.api.common.entity.Bond;

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
