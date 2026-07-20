package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.invest.api.common.entity.Bond;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BondRepository extends JpaRepository<Bond, Long> {
    Optional<Bond> findByUid(String uid);

    @Query("""
                select b from Bond b
                where upper(b.currency) in :currencies or upper(b.nominalCurrency) in :currencies
            """)
    List<Bond> findByCurrencyIn(@Param("currencies") Collection<String> currencies);

    @Query("""
                select b from Bond b
                where b.isFixedCoupon = false or b.id not in (select distinct c.bond.id from Coupon c)
            """)
    List<Bond> findBondsRequiringCouponRefresh();
}
