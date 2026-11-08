package ru.invest.api.alert.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.alert.service.config.AlertProperties;
import ru.invest.api.alert.service.model.AlertModel;
import ru.invest.api.alert.service.model.NotificationModel;
import ru.invest.api.alert.service.usecase.AlertCheckUseCase;
import ru.invest.api.alert.service.usecase.AlertUseCase;
import ru.invest.api.alert.service.usecase.NotificationDispatchUseCase;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.BondAlert;
import ru.invest.api.common.entity.Notification;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.alert.AlertType;
import ru.invest.api.common.repository.BondAlertRepository;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.repository.NotificationRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertUseCaseImpl implements AlertUseCase {
    private static final String NOT_FOUND = "Alert %d not found";

    private final AlertProperties properties;
    private final BondAlertRepository alertRepository;
    private final NotificationRepository notificationRepository;
    private final BondRepository bondRepository;
    private final BondUseCase bondUseCase;
    private final AlertCheckUseCase alertCheckUseCase;
    private final NotificationDispatchUseCase dispatchUseCase;

    @Override
    @Transactional(readOnly = true)
    public List<AlertModel> getAlerts(final String userId, final String ticker) {
        final List<BondAlert> alerts = ticker == null
                ? alertRepository.findByUserIdOrderByTickerAscTypeAsc(userId)
                : alertRepository.findByUserIdAndTickerOrderByTypeAsc(userId, ticker);
        final Map<String, Bond> bonds = bondRepository.findByTickerIn(alerts.stream().map(BondAlert::getTicker)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Bond::getTicker, Function.identity(), (first, second) -> first));
        return alerts.stream().map(alert -> toModel(alert, bonds.get(alert.getTicker()))).toList();
    }

    @Override
    public AlertModel create(final String userId, final String ticker, final AlertType type, final Integer days) {
        validateDays(days);
        // 404, если такой облигации нет
        bondUseCase.getWithSchedule(ticker);
        if (alertRepository.existsByUserIdAndTickerAndType(userId, ticker, type)) {
            throw new GeneralUnprocessableEntityException(ExceptionErrorCode.ALERT_INVALID,
                    "Alert %s for %s already exists".formatted(type, ticker));
        }
        if (alertRepository.countByUserId(userId) >= properties.getMaxAlertsPerUser()) {
            throw new GeneralUnprocessableEntityException(ExceptionErrorCode.ALERT_INVALID,
                    "No more than %d alerts per user".formatted(properties.getMaxAlertsPerUser()));
        }
        final BondAlert alert = alertRepository.save(new BondAlert()
                .setUserId(userId)
                .setTicker(ticker)
                .setType(type)
                .setDays(days)
                .setEnabled(true)
                .setCreatedAt(LocalDateTime.now(ZoneOffset.UTC)));
        checkNow(alert.getId());
        return reload(userId, alert.getId());
    }

    @Override
    public AlertModel update(final String userId, final Long id, final Integer days, final Boolean enabled) {
        final BondAlert alert = find(userId, id);
        if (days != null) {
            validateDays(days);
        }
        final boolean changed = days != null && !days.equals(alert.getDays())
                || enabled != null && enabled != alert.isEnabled();
        if (days != null) {
            alert.setDays(days);
        }
        if (enabled != null) {
            alert.setEnabled(enabled);
        }
        alertRepository.save(alert);
        // окно стало шире или правило включили - событие могло уже попасть в окно
        if (changed && alert.isEnabled()) {
            checkNow(alert.getId());
        }
        return reload(userId, id);
    }

    @Override
    @Transactional
    public void delete(final String userId, final Long id) {
        alertRepository.delete(find(userId, id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationModel> getNotifications(final String userId, final Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable).map(this::toModel);
    }

    private void checkNow(final Long alertId) {
        if (alertCheckUseCase.check(alertId, LocalDate.now(properties.getZone())) > 0) {
            dispatchUseCase.dispatchSoon();
        }
    }

    private AlertModel reload(final String userId, final Long id) {
        final BondAlert alert = find(userId, id);
        return toModel(alert, bondRepository.findByTickerIn(List.of(alert.getTicker())).stream().findFirst()
                .orElse(null));
    }

    private BondAlert find(final String userId, final Long id) {
        return alertRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.NOT_FOUND,
                        NOT_FOUND.formatted(id)));
    }

    private void validateDays(final Integer days) {
        if (days == null || days < 1 || days > properties.getMaxDays()) {
            throw new GeneralUnprocessableEntityException(ExceptionErrorCode.ALERT_INVALID,
                    "days must be from 1 to %d".formatted(properties.getMaxDays()));
        }
    }

    private AlertModel toModel(final BondAlert alert, final Bond bond) {
        return new AlertModel(alert.getId(), alert.getTicker(),
                bond == null ? null : bond.getName(),
                bond == null ? null : bond.getIsin(),
                alert.getType(), alert.getDays(), alert.isEnabled(), alert.getCreatedAt(), alert.getLastTriggeredAt());
    }

    private NotificationModel toModel(final Notification notification) {
        return new NotificationModel(notification.getId(), notification.getAlertId(), notification.getTicker(),
                notification.getText(), notification.getStatus(), notification.getAttempts(),
                notification.getCreatedAt(), notification.getSentAt(),
                notification.getLastError());
    }
}
