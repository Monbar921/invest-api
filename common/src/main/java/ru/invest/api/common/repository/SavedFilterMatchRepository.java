package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.invest.api.common.entity.SavedFilterMatch;

import java.util.List;

public interface SavedFilterMatchRepository extends JpaRepository<SavedFilterMatch, Long> {
    List<SavedFilterMatch> findByFilterId(Long filterId);

    @Modifying
    @Query("delete from SavedFilterMatch m where m.filterId = :filterId")
    int deleteByFilterId(Long filterId);
}
