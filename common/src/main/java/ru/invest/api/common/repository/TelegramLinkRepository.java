package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.invest.api.common.entity.TelegramLink;

import java.util.Optional;

public interface TelegramLinkRepository extends JpaRepository<TelegramLink, String> {
    Optional<TelegramLink> findByChatId(Long chatId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TelegramLink l where l.chatId = :chatId")
    int deleteByChatId(Long chatId);
}
