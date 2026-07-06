package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.invest.api.common.entity.Coupon;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
}
