package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.invest.api.common.entity.PortfolioBroker;

import java.util.List;
import java.util.Optional;

public interface PortfolioBrokerRepository extends JpaRepository<PortfolioBroker, Long> {
    List<PortfolioBroker> findByUserIdOrderByNameAsc(String userId);

    Optional<PortfolioBroker> findByUserIdAndNameIgnoreCase(String userId, String name);

    long countByUserId(String userId);
}
