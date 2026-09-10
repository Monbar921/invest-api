package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.invest.api.common.entity.CouponData;

import java.util.List;
import java.util.Set;

public interface CouponRepository extends JpaRepository<CouponData, Long> {
    List<CouponData> findByTicker(String ticker);

    List<CouponData> findByTickerIn(Set<String> tickers);
}
