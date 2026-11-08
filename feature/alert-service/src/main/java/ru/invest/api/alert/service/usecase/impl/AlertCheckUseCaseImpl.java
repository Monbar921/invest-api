package ru.invest.api.alert.service.usecase.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.invest.api.alert.service.usecase.AlertCheckUseCase;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.entity.BondAlert;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.event.BondEventModel;
import ru.invest.api.common.repository.BondAlertRepository;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.usecase.BondEventUseCase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AlertCheckUseCaseImpl implements AlertCheckUseCase {
    private final BondAlertRepository alertRepository;
    private final BondRepository bondRepository;
    private final BondUseCase bondUseCase;
    private final BondEventUseCase bondEventUseCase;
    private final NotificationQueue notificationQueue;
    private final TransactionTemplate transaction;

    public AlertCheckUseCaseImpl(final BondAlertRepository alertRepository,
                                 final BondRepository bondRepository,
                                 final BondUseCase bondUseCase,
                                 final BondEventUseCase bondEventUseCase,
                                 final NotificationQueue notificationQueue,
                                 final PlatformTransactionManager transactionManager) {
        this.alertRepository = alertRepository;
        this.bondRepository = bondRepository;
        this.bondUseCase = bondUseCase;
        this.bondEventUseCase = bondEventUseCase;
        this.notificationQueue = notificationQueue;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public int checkAll(final LocalDate today) {
        // только id: сами правила читаются заново внутри транзакции облигации, иначе отметка о срабатывании
        // (lastTriggeredAt) ушла бы в отсоединённую сущность и не сохранилась
        final Map<String, List<Long>> byTicker = alertRepository.findByEnabledTrue()
                .stream()
                .collect(Collectors.groupingBy(BondAlert::getTicker,
                        Collectors.mapping(BondAlert::getId, Collectors.toList())));

        int queued = 0;
        for (final Map.Entry<String, List<Long>> entry : byTicker.entrySet()) {
            try {
                // своя транзакция на облигацию: ошибка по одной не откатывает уведомления по остальным
                queued += Optional.ofNullable(transaction.execute(status -> checkTicker(entry.getKey(),
                        alertRepository.findAllById(entry.getValue()).stream().filter(BondAlert::isEnabled).toList(),
                        today))).orElse(0);
            } catch (final RuntimeException e) {
                log.error("Alert check failed for {}", entry.getKey(), e);
            }
        }
        log.info("Alert check: {} rules, {} bonds, {} notifications queued", byTicker.values().stream()
                .mapToInt(List::size).sum(), byTicker.size(), queued);
        return queued;
    }

    @Override
    public int check(final Long alertId, final LocalDate today) {
        return Optional.ofNullable(transaction.execute(status -> alertRepository.findById(alertId)
                .filter(BondAlert::isEnabled)
                .filter(alert -> alert.getType().isDateRule())
                .map(alert -> checkTicker(alert.getTicker(), List.of(alert), today))
                .orElse(0))).orElse(0);
    }

    private int checkTicker(final String ticker, final List<BondAlert> alerts, final LocalDate today) {
        // наличие - заранее, а не по исключению: GeneralNotFoundEntityException из транзакционного метода пометил бы
        // нашу транзакцию на откат, и уведомление «облигации больше нет» не сохранилось бы
        if (bondRepository.findByTicker(ticker).isEmpty()) {
            return bondGone(ticker, alerts);
        }
        final BondModel bond = bondUseCase.getWithSchedule(ticker);
        final List<BondEventModel> events = bondEventUseCase.getEvents(bond, today);

        int queued = 0;
        for (final BondAlert alert : alerts) {
            // правила по цене и доходности проверяются после обновления цен; здесь - только пропажа облигации
            if (alert.getType().isDateRule()) {
                queued += checkAlert(alert, bond, events, today);
            }
        }
        return queued;
    }

    /**
     * События нужного типа с сегодня по сегодня + days. Окно, а не точный день: если в день «за 3 дня» приложение
     * не работало, уведомление придёт при следующей проверке, пока событие не прошло.
     */
    private int checkAlert(final BondAlert alert, final BondModel bond, final List<BondEventModel> events,
                           final LocalDate today) {
        final LocalDate until = today.plusDays(alert.getDays());
        int queued = 0;
        for (final BondEventModel event : events) {
            if (event.getType() != alert.getType().getEventType() || event.getDate().isAfter(until)) {
                continue;
            }
            final String dedupKey = "%s:%d:%s".formatted(alert.getType(), alert.getId(), event.getDate());
            final String text = AlertTexts.event(alert.getType(), event, today, bond.getName(), alert.getTicker());
            if (notificationQueue.enqueue(alert.getUserId(), alert.getId(), alert.getTicker(), dedupKey, text)) {
                queued++;
            }
        }
        if (queued > 0) {
            alert.setLastTriggeredAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        return queued;
    }

    /**
     * Облигации больше нет (синхронизация удаляет пропавшие из T-Invest API): правила удаляются, пользователь
     * узнаёт об этом один раз. История остаётся - alert_id в ней обнулит внешний ключ.
     */
    private int bondGone(final String ticker, final List<BondAlert> alerts) {
        int queued = 0;
        for (final BondAlert alert : alerts) {
            if (notificationQueue.enqueue(alert.getUserId(), null, ticker, "BOND_GONE:%s:%s".formatted(
                    alert.getUserId(), ticker), AlertTexts.bondGone(ticker))) {
                queued++;
            }
            alertRepository.delete(alert);
        }
        log.info("Bond {} is gone: {} alerts deleted", ticker, alerts.size());
        return queued;
    }
}
