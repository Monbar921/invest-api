package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.invest.api.common.entity.BrokerConnection;

import java.util.Optional;

public interface BrokerConnectionRepository extends JpaRepository<BrokerConnection, Long> {
    Optional<BrokerConnection> findByUserIdAndProvider(String userId, String provider);
}
