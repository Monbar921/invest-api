package ru.invest.api.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Чат Telegram, привязанный к пользователю. Пользователь - sub из Keycloak, его передаёт gateway.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "telegram_link")
public class TelegramLink {
    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "chat_id")
    private Long chatId;

    /** Имя пользователя в Telegram (@username) на момент привязки; может отсутствовать */
    @Column(name = "username")
    private String username;

    /** UTC */
    @Column(name = "linked_at")
    private LocalDateTime linkedAt;
}
