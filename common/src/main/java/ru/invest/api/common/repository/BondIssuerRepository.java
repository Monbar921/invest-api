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

    List<BondIssuer> findByEmitterId(Long emitterId);

    /**
     * Эмитенты, у которых есть хотя бы один выпуск из системы, - для sitemap.
     */
    @Query("""
            select distinct i.emitterId from BondIssuer i
            where i.emitterId is not null
              and exists (select 1 from Bond b where b.isin = i.isin)
            order by i.emitterId
            """)
    List<Long> findEmitterIdsWithBonds();

    @Query("select max(i.updated.committedAt) from BondIssuer i")
    Optional<LocalDateTime> findLastUpdate();
}
