package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.invest.api.common.entity.Coupon;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    @Query("""
                select c from Coupon c
                    left join fetch c.couponData
                    where c.uid = :uid
            """)
    Optional<Coupon> findByUid(String uid);
}
