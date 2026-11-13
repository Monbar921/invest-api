package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.invest.api.common.entity.PortfolioSale;

import java.util.List;
import java.util.Optional;

public interface PortfolioSaleRepository extends JpaRepository<PortfolioSale, Long> {
    List<PortfolioSale> findByUserIdOrderByTickerAscSellDateAscIdAsc(String userId);

    List<PortfolioSale> findByUserIdAndTicker(String userId, String ticker);

    Optional<PortfolioSale> findByIdAndUserId(Long id, String userId);

    long countByUserId(String userId);
}
