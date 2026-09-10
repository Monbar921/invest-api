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
    Optional<Bond> findByUid(String uid);

    @Query("""
            select b from Bond b
            where b.isFixedCoupon = false
              or b.couponData is empty
            """)
    Slice<Bond> findByNotFixedCouponAndEmptyCoupons(Pageable pageable);

    List<Bond> findByTickerIn(Set<String> tickers);
}
