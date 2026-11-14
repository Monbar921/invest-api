package ru.invest.api.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Подключение пользователя к брокеру для импорта сделок: токен T-Invest только на чтение. Токен хранится зашифрованным
 * (AES-GCM, ключ - в конфигурации), открытым не пишется ни в БД, ни в лог.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@ToString(exclude = "tokenEncrypted")
@Table(name = "broker_connection")
public class BrokerConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** sub Keycloak */
    @Column(name = "user_id")
    private String userId;

    /** Источник: TINVEST */
    @Column(name = "provider")
    private String provider;

    /** Зашифрованный токен: версия формата, IV и шифртекст в Base64 */
    @Column(name = "token_encrypted")
    private String tokenEncrypted;

    /** Последние 4 символа токена - чтобы пользователь узнал, какой токен подключён */
    @Column(name = "token_hint")
    private String tokenHint;

    /** UTC */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** UTC */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Последний предпросмотр импорта по токену, UTC; null - ещё не было */
    @Column(name = "last_import_at")
    private LocalDateTime lastImportAt;
}
