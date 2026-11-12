package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.invest.api.common.entity.PortfolioCalendarToken;

import java.util.Optional;

public interface PortfolioCalendarTokenRepository extends JpaRepository<PortfolioCalendarToken, String> {
    Optional<PortfolioCalendarToken> findByTokenHash(String tokenHash);
}
