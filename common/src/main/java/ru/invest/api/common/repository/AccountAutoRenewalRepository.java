package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.invest.api.common.entity.AccountAutoRenewal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccountAutoRenewalRepository extends JpaRepository<AccountAutoRenewal, Long> {
    /** Действующее автопродление пользователя */
    Optional<AccountAutoRenewal> findByUserIdAndCancelledAtIsNull(String userId);

    /** Автопродление уже включалось этим платежом: повторный webhook не включает его снова */
    boolean existsByPaymentId(Long paymentId);

    /** Действующие автопродления, которым пора списывать, - id: каждое обрабатывается в своей транзакции */
    @Query("select r.id from AccountAutoRenewal r where r.cancelledAt is null and r.nextChargeAt <= :now"
            + " order by r.nextChargeAt")
    List<Long> findDueIds(@Param("now") LocalDateTime now);
}
