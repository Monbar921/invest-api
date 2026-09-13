package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.invest.api.common.entity.CouponData;

import java.util.List;
import java.util.Set;

public interface CouponDataRepository extends JpaRepository<CouponData, Long> {
    List<CouponData> findByUidIn(Set<String> tickers);
}
