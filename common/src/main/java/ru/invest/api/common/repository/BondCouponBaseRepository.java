package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.invest.api.common.entity.BondCouponBase;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BondCouponBaseRepository extends JpaRepository<BondCouponBase, String> {
    List<BondCouponBase> findByIsinIn(Collection<String> isins);

    @Query("select max(b.updated.committedAt) from BondCouponBase b")
    Optional<LocalDateTime> findLastUpdate();
}
