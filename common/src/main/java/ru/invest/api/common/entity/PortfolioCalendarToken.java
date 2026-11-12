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
 * Личная ссылка на календарь выплат портфеля (.ics) - одна на пользователя. Хранится SHA-256 токена: по таблице ссылку
 * не восстановить.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "portfolio_calendar_token")
public class PortfolioCalendarToken {
    /** sub Keycloak */
    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "token_hash")
    private String tokenHash;

    /** UTC */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
