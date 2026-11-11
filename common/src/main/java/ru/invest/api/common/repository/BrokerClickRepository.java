package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.invest.api.common.entity.BrokerClick;

import java.time.LocalDate;
import java.util.List;

public interface BrokerClickRepository extends JpaRepository<BrokerClick, Long> {
    /**
     * +1 к счётчику дня одним запросом: параллельные клики не теряются и не падают на уникальном ключе.
     */
    @Modifying
    @Query(value = """
            insert into broker_click (click_date, partner_id, ticker, clicks) values (:date, :partnerId, :ticker, 1)
            on conflict (click_date, partner_id, ticker) do update set clicks = broker_click.clicks + 1
            """, nativeQuery = true)
    void increment(LocalDate date, String partnerId, String ticker);

    List<BrokerClick> findByClickDateBetweenOrderByClickDateAscPartnerIdAscTickerAsc(LocalDate from, LocalDate to);
}
