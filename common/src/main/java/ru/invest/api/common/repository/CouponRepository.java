package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.invest.api.common.entity.CouponData;

import java.util.List;

public interface CouponRepository extends JpaRepository<CouponData, Long> {
    @Query("""
                select c from CouponData c
                join fetch c.bond b
                where b.ticker = :ticker
            """)
    List<CouponData> findByTicker(@Param("ticker") String ticker);

    void deleteByBond_Uid(String uid);
}
