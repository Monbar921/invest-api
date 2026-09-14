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

    List<Bond> findByUidIn(Set<String> tickers);

    Optional<Bond> findByUid(String uid);
}
