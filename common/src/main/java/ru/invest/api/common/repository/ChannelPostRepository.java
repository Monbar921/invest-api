package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.invest.api.common.entity.ChannelPost;
import ru.invest.api.common.model.channel.ChannelPostStatus;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ChannelPostRepository extends JpaRepository<ChannelPost, Long> {
    boolean existsByDedupKey(String dedupKey);

    /** Следующий пост к публикации: самый старый из готовых */
    Optional<ChannelPost> findFirstByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAscIdAsc(
            ChannelPostStatus status, LocalDateTime now);

    /** Последний опубликованный - для паузы между постами */
    Optional<ChannelPost> findFirstByStatusOrderBySentAtDesc(ChannelPostStatus status);

    /** Сколько опубликовано с начала дня - для лимита постов в день */
    long countByStatusAndSentAtGreaterThanEqual(ChannelPostStatus status, LocalDateTime from);

    @Modifying
    @Query("""
            update ChannelPost p set p.status = :skipped, p.lastError = :reason
            where p.status = :pending and p.expiresAt < :now
            """)
    int expire(ChannelPostStatus pending, ChannelPostStatus skipped, LocalDateTime now, String reason);

    @Modifying
    @Query("delete from ChannelPost p where p.status <> :pending and p.createdAt < :before")
    int deleteFinishedBefore(ChannelPostStatus pending, LocalDateTime before);
}
