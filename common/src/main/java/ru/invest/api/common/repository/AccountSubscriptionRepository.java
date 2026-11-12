package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.invest.api.common.entity.AccountSubscription;

import java.time.LocalDateTime;
import java.util.List;

public interface AccountSubscriptionRepository extends JpaRepository<AccountSubscription, Long> {
    /**
     * Подписки, действующие в момент now: не отозваны, начались и не истекли. Последняя выданная - первой.
     */
    @Query("select s from AccountSubscription s where s.userId = :userId and s.revokedAt is null"
            + " and s.validFrom <= :now and (s.validUntil is null or s.validUntil > :now)"
            + " order by s.validFrom desc, s.id desc")
    List<AccountSubscription> findActive(@Param("userId") String userId, @Param("now") LocalDateTime now);
}
