package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.invest.api.common.entity.Coupon;

import java.util.List;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    @Query("""
                select c from Coupon c
                join fetch c.bond b
                where b.ticker = :ticker
            """)
    List<Coupon> findByTicker(@Param("ticker") String ticker);
}
