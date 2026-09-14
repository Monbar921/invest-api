package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.invest.api.common.entity.Coupon;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    @Query("""
                select c from Coupon c
                    left join fetch c.couponData
                    where c.uid = :uid
            """)
    Optional<Coupon> findByUid(String uid);

    @Query("""
                select c from Coupon c
                    left join fetch c.bond b
                    left join fetch c.couponData cd
                    where c.uid is not null and c.uid in :uids
            """)
    List<Coupon> findByUidIn(Set<String> uids);
}
