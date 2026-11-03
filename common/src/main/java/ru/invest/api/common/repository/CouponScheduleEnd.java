package ru.invest.api.common.repository;

import java.time.LocalDateTime;

/**
 * Дата последнего загруженного купона облигации рядом с датой её погашения.
 */
public interface CouponScheduleEnd {
    String getUid();

    String getTicker();

    LocalDateTime getMaturityDate();

    LocalDateTime getLastPaymentDate();
}
