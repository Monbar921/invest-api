package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.invest.api.common.entity.PortfolioPosition;

import java.util.List;
import java.util.Optional;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {
    List<PortfolioPosition> findByUserIdOrderByTickerAscBuyDateAscIdAsc(String userId);

    Optional<PortfolioPosition> findByIdAndUserId(Long id, String userId);

    long countByUserId(String userId);
}
