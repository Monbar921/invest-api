package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.invest.api.common.entity.PortfolioSale;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PortfolioSaleRepository extends JpaRepository<PortfolioSale, Long> {
    List<PortfolioSale> findByUserIdOrderByTickerAscSellDateAscIdAsc(String userId);

    List<PortfolioSale> findByUserIdAndTicker(String userId, String ticker);

    Optional<PortfolioSale> findByIdAndUserId(Long id, String userId);

    long countByUserId(String userId);

    /** Ключи импорта сделок пользователя - какие сделки источника уже в портфеле */
    @Query("select e.importKey from PortfolioSale e where e.userId = :userId and e.importKey is not null")
    Set<String> findImportKeys(@Param("userId") String userId);
}
