package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.invest.api.common.entity.TelegramLinkToken;

import java.time.LocalDateTime;

public interface TelegramLinkTokenRepository extends JpaRepository<TelegramLinkToken, String> {
    @Modifying
    @Query("delete from TelegramLinkToken t where t.userId = :userId or t.expiresAt < :now")
    void deleteByUserIdOrExpired(String userId, LocalDateTime now);
}
