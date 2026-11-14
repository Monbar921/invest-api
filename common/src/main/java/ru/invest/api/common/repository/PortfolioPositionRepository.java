package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.invest.api.common.entity.PortfolioPosition;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {
    List<PortfolioPosition> findByUserIdOrderByTickerAscBuyDateAscIdAsc(String userId);

    List<PortfolioPosition> findByUserIdAndTicker(String userId, String ticker);

    Optional<PortfolioPosition> findByIdAndUserId(Long id, String userId);

    long countByUserId(String userId);

    /** Ключи импорта сделок пользователя - какие сделки источника уже в портфеле */
    @Query("select e.importKey from PortfolioPosition e where e.userId = :userId and e.importKey is not null")
    Set<String> findImportKeys(@Param("userId") String userId);
}
