package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.invest.api.common.entity.BondAlert;
import ru.invest.api.common.model.alert.AlertType;

import java.util.List;
import java.util.Optional;

public interface BondAlertRepository extends JpaRepository<BondAlert, Long> {
    List<BondAlert> findByUserIdOrderByTickerAscTypeAsc(String userId);

    List<BondAlert> findByUserIdAndTickerOrderByTypeAsc(String userId, String ticker);

    Optional<BondAlert> findByIdAndUserId(Long id, String userId);

    boolean existsByUserIdAndTickerAndType(String userId, String ticker, AlertType type);

    long countByUserId(String userId);

    List<BondAlert> findByEnabledTrue();
}
