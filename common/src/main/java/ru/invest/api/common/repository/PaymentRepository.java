package ru.invest.api.common.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.invest.api.common.entity.Payment;
import ru.invest.api.common.model.payment.PaymentStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    /**
     * Платёж под блокировкой строки: webhook и опрос статуса могут прийти одновременно, применить платёж должен один.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    Optional<Payment> findByProviderAndOrderId(String provider, String orderId);

    Optional<Payment> findByIdAndUserId(Long id, String userId);

    /** История платежей пользователя, новые первыми */
    List<Payment> findByUserIdAndStatusNotOrderByCreatedAtDescIdDesc(String userId, PaymentStatus status);

    /** Списание автопродления без итога: пока оно есть, новое не создаётся */
    Optional<Payment> findFirstByAutoRenewalIdAndStatusInOrderByIdDesc(Long autoRenewalId,
                                                                     Collection<PaymentStatus> statuses);
}
