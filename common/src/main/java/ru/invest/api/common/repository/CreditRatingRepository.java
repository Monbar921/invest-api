package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.invest.api.common.entity.CreditRating;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface CreditRatingRepository extends JpaRepository<CreditRating, Long> {
    /**
     * Действующие рейтинги: не отозваны и с действием не раньше даты (рейтинг без пересмотра дольше срока не верен).
     */
    @Query("""
            select r from CreditRating r
            where r.withdrawn = false and r.score is not null and r.actionDate >= :since
              and (r.isin in :isins or r.emitterId in :emitterIds)
            """)
    List<CreditRating> findActive(Collection<String> isins, Collection<Long> emitterIds, LocalDate since);

    @Query("""
            select r from CreditRating r
            where (r.isin is not null and r.isin in :isins) or (r.emitterId is not null and r.emitterId in :emitterIds)
            """)
    List<CreditRating> findBySubjects(Collection<String> isins, Collection<Long> emitterIds);
}
