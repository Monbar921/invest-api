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
 * Одноразовый токен привязки чата: ссылка t.me/&lt;bot&gt;?start=&lt;токен&gt;. Хранится SHA-256 токена.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "telegram_link_token")
public class TelegramLinkToken {
    @Id
    @Column(name = "token_hash")
    private String tokenHash;

    @Column(name = "user_id")
    private String userId;

    /** UTC */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
