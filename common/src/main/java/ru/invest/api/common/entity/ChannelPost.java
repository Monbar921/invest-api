package ru.invest.api.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import ru.invest.api.common.model.channel.ChannelPostKind;
import ru.invest.api.common.model.channel.ChannelPostStatus;

import java.time.LocalDateTime;

/**
 * Пост публичного Telegram-канала в очереди публикации. Все времена - UTC.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "channel_post")
public class ChannelPost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind")
    private ChannelPostKind kind;

    /** Ключ против повторов: вид поста и id новости или неделя подборки */
    @Column(name = "dedup_key")
    private String dedupKey;

    @Column(name = "text")
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ChannelPostStatus status;

    @Column(name = "attempts")
    private int attempts;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    /** После этого момента пост неактуален и не публикуется (SKIPPED) */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /** id сообщения в канале - найти пост, если его придётся исправить или удалить вручную */
    @Column(name = "message_id")
    private Integer messageId;

    @Column(name = "last_error")
    private String lastError;
}
