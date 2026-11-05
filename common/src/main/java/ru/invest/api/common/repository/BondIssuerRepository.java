package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.invest.api.common.entity.BondIssuer;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BondIssuerRepository extends JpaRepository<BondIssuer, Long> {
    List<BondIssuer> findByIsinIn(Collection<String> isins);

    List<BondIssuer> findByEmitterIdIn(Collection<Long> emitterIds);

    Optional<BondIssuer> findByIsin(String isin);

    @Query("select max(i.updated.committedAt) from BondIssuer i")
    Optional<LocalDateTime> findLastUpdate();
}
