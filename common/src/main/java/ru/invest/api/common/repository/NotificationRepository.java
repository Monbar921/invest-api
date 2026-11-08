package ru.invest.api.common.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.invest.api.common.entity.Notification;
import ru.invest.api.common.model.alert.NotificationStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByDedupKey(String dedupKey);

    @Query("""
            select n from Notification n
            where n.status = :status and n.nextAttemptAt <= :now
            order by n.nextAttemptAt, n.id
            """)
    List<Notification> findDue(NotificationStatus status, LocalDateTime now, Pageable pageable);

    Page<Notification> findByUserIdOrderByCreatedAtDescIdDesc(String userId, Pageable pageable);

    @Modifying
    @Query("delete from Notification n where n.status <> :pending and n.createdAt < :before")
    int deleteFinishedBefore(NotificationStatus pending, LocalDateTime before);
}
