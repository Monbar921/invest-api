package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.invest.api.common.entity.SavedFilter;

import java.util.List;
import java.util.Optional;

public interface SavedFilterRepository extends JpaRepository<SavedFilter, Long> {
    List<SavedFilter> findByUserIdOrderByNameAscIdAsc(String userId);

    Optional<SavedFilter> findByIdAndUserId(Long id, String userId);

    boolean existsByUserIdAndNameIgnoreCase(String userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(String userId, String name, Long id);

    long countByUserId(String userId);
}
