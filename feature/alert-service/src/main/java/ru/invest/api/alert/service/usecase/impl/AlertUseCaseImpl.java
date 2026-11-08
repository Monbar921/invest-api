package ru.invest.api.alert.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.alert.service.config.AlertProperties;
import ru.invest.api.alert.service.model.AlertChangeModel;
import ru.invest.api.alert.service.model.AlertModel;
import ru.invest.api.alert.service.model.AlertRuleModel;
import ru.invest.api.alert.service.model.NotificationModel;
import ru.invest.api.alert.service.usecase.AlertCheckUseCase;
import ru.invest.api.alert.service.usecase.AlertUseCase;
import ru.invest.api.alert.service.usecase.NotificationDispatchUseCase;
import ru.invest.api.alert.service.usecase.PriceAlertCheckUseCase;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.BondAlert;
import ru.invest.api.common.entity.Notification;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.alert.AlertDirection;
import ru.invest.api.common.model.alert.AlertState;
import ru.invest.api.common.model.alert.AlertType;
import ru.invest.api.common.model.event.BondEventModel;
import ru.invest.api.common.model.event.BondEventType;
import ru.invest.api.common.repository.BondAlertRepository;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.repository.NewsRepository;
import ru.invest.api.common.repository.NotificationRepository;
import ru.invest.api.common.usecase.BondEventUseCase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")
public class AlertUseCaseImpl implements AlertUseCase {
    private static final String NOT_FOUND = "Alert %d not found";
    // пределы порогов: уровень цены в % от номинала, доходность в % годовых, изменение цены в %
    private static final BigDecimal MIN_PRICE = BigDecimal.ONE;
    private static final BigDecimal MAX_PRICE = BigDecimal.valueOf(300);
    private static final BigDecimal MAX_YIELD = BigDecimal.valueOf(100);
    private static final BigDecimal MIN_CHANGE = new BigDecimal("0.1");
    private static final BigDecimal MAX_CHANGE = BigDecimal.valueOf(50);

    private final AlertProperties properties;
    private final BondAlertRepository alertRepository;
    private final NotificationRepository notificationRepository;
    private final BondRepository bondRepository;
    private final BondUseCase bondUseCase;
    private final AlertCheckUseCase alertCheckUseCase;
    private final PriceAlertCheckUseCase priceAlertCheckUseCase;
    private final NewsRepository newsRepository;
    private final BondEventUseCase bondEventUseCase;
    private final NotificationDispatchUseCase dispatchUseCase;

    @Override
    @Transactional(readOnly = true)
    public List<AlertModel> getAlerts(final String userId, final String ticker) {
        final List<BondAlert> alerts = ticker == null
                ? alertRepository.findByUserIdOrderByTickerAscTypeAsc(userId)
                : alertRepository.findByUserIdAndTickerOrderByTypeAsc(userId, ticker);
        final Map<String, Bond> bonds = bondRepository.findByTickerIn(alerts.stream().map(BondAlert::getTicker)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Bond::getTicker, Function.identity(), (first, second) -> first));
        return alerts.stream().map(alert -> toModel(alert, bonds.get(alert.getTicker()))).toList();
    }

    @Override
    public AlertModel create(final String userId, final AlertRuleModel rule) {
        final AlertType type = rule.type();
        validate(type, rule.days(), rule.direction(), rule.threshold());
        if (type.hasBond() && (rule.ticker() == null || rule.ticker().isBlank())) {
            throw unprocessable("ticker is required for %s".formatted(type));
        }
        final String ticker = type.hasBond() ? rule.ticker() : null;
        // 404, если такой облигации нет
        final BondModel bond = ticker == null ? null : bondUseCase.getWithSchedule(ticker);
        final boolean exists = ticker == null
                ? alertRepository.existsByUserIdAndType(userId, type)
                : !type.isPriceRule() && alertRepository.existsByUserIdAndTickerAndType(userId, ticker, type);
        if (exists) {
            throw unprocessable("Alert %s%s already exists".formatted(type, ticker == null ? "" : " for " + ticker));
        }
        if (alertRepository.countByUserId(userId) >= properties.getMaxAlertsPerUser()) {
            throw unprocessable("No more than %d alerts per user".formatted(properties.getMaxAlertsPerUser()));
        }
        final BondAlert alert = new BondAlert()
                .setUserId(userId)
                .setTicker(ticker)
                .setType(type)
                .setEnabled(true)
                .setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        if (type.isDateRule()) {
            alert.setDays(rule.days());
        } else if (type.isPriceRule()) {
            alert.setDirection(rule.direction()).setThreshold(rule.threshold());
            arm(alert, bond);
        } else {
            startFromNow(alert, bond);
        }
        checkNow(alertRepository.save(alert));
        return reload(userId, alert.getId());
    }

    @Override
    public AlertModel update(final String userId, final Long id, final AlertChangeModel change) {
        final BondAlert alert = find(userId, id);
        final AlertType type = alert.getType();
        final Integer days = type.isDateRule() ? Optional.ofNullable(change.days()).orElse(alert.getDays()) : null;
        final AlertDirection direction = type.isPriceRule()
                ? Optional.ofNullable(change.direction()).orElse(alert.getDirection())
                : null;
        final BigDecimal threshold = type.isPriceRule()
                ? Optional.ofNullable(change.threshold()).orElse(alert.getThreshold())
                : null;
        validate(type, days, direction, threshold);

        final boolean enabling = Boolean.TRUE.equals(change.enabled()) && !alert.isEnabled();
        final boolean changed = enabling
                || !Objects.equals(days, alert.getDays())
                || direction != alert.getDirection()
                || threshold != null && alert.getThreshold() != null && threshold.compareTo(alert.getThreshold()) != 0;
        alert.setDays(days).setDirection(direction).setThreshold(threshold);
        if (change.enabled() != null) {
            alert.setEnabled(change.enabled());
        }
        // новый порог или включение заново - порог взводится, изменение от цены считается от текущей цены
        if (type.isPriceRule() && (enabling || changed)) {
            arm(alert, bondUseCase.getWithSchedule(alert.getTicker()));
        }
        // включили заново - без пачки накопившихся новостей и купонов
        if (!type.isDateRule() && !type.isPriceRule() && enabling) {
            startFromNow(alert, alert.getTicker() == null ? null : bondUseCase.getWithSchedule(alert.getTicker()));
        }
        alertRepository.save(alert);
        if (changed && alert.isEnabled()) {
            checkNow(alert);
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

    /**
     * Порог уровня - взведён; изменение от цены - от текущей цены. Нет цены - изменение от неё не задать.
     */
    private void arm(final BondAlert alert, final BondModel bond) {
        if (alert.getType().isLevel()) {
            alert.setState(AlertState.ARMED);
        }
        if (alert.getType() == AlertType.PRICE_CHANGE) {
            final BigDecimal price = Optional.ofNullable(bond.getPrice()).map(PriceModel::getPercentagePrice)
                    .orElseThrow(() -> unprocessable("Bond %s has no price yet".formatted(bond.getTicker())));
            alert.setBaseValue(price);
        }
    }

    /**
     * Правила по новостям и объявленным купонам уведомляют только о новом: отметка - на последней загруженной новости
     * и последнем купоне с уже известным размером.
     */
    private void startFromNow(final BondAlert alert, final BondModel bond) {
        if (alert.getType().isNewsRule()) {
            alert.setCursorId(newsRepository.findMaxId().orElse(0L));
        }
        if (alert.getType() == AlertType.FLOATER_COUPON && bond != null) {
            alert.setCursorDate(bondEventUseCase.getEvents(bond, LocalDate.now(properties.getZone())).stream()
                    .filter(event -> event.getType() == BondEventType.COUPON && event.getAmount() != null)
                    .map(BondEventModel::getDate)
                    .max(LocalDate::compareTo)
                    .orElse(null));
        }
    }

    /** Событие уже в окне или порог уже пересечён - уведомление сразу, без ожидания проверки */
    private void checkNow(final BondAlert alert) {
        final LocalDate today = LocalDate.now(properties.getZone());
        final int queued;
        if (alert.getType().isDateRule()) {
            queued = alertCheckUseCase.check(alert.getId(), today);
        } else if (alert.getType().isPriceRule()) {
            queued = priceAlertCheckUseCase.check(alert.getId(), today);
        } else {
            // новости и объявленные купоны - только новые, сразу проверять нечего
            queued = 0;
        }
        if (queued > 0) {
            dispatchUseCase.dispatchSoon();
        }
    }

    private void validate(final AlertType type, final Integer days, final AlertDirection direction,
                          final BigDecimal threshold) {
        if (type == null) {
            throw unprocessable("type is required");
        }
        if (type.isDateRule()) {
            if (days == null || days < 1 || days > properties.getMaxDays()) {
                throw unprocessable("days must be from 1 to %d".formatted(properties.getMaxDays()));
            }
            return;
        }
        if (!type.isPriceRule()) {
            // новости, рейтинги, ключевая ставка, объявленный купон - без параметров
            return;
        }
        if (direction == null || threshold == null) {
            throw unprocessable("direction and threshold are required for %s".formatted(type));
        }
        if (type.isLevel() && direction == AlertDirection.ANY) {
            throw unprocessable("direction must be UP or DOWN for %s".formatted(type));
        }
        final BigDecimal min = switch (type) {
            case PRICE_LEVEL -> MIN_PRICE;
            case YTM_LEVEL, YTO_LEVEL -> BigDecimal.ZERO;
            default -> MIN_CHANGE;
        };
        final BigDecimal max = switch (type) {
            case PRICE_LEVEL -> MAX_PRICE;
            case YTM_LEVEL, YTO_LEVEL -> MAX_YIELD;
            default -> MAX_CHANGE;
        };
        final boolean minExclusive = type == AlertType.YTM_LEVEL || type == AlertType.YTO_LEVEL;
        if (threshold.compareTo(max) > 0
                || (minExclusive ? threshold.compareTo(min) <= 0 : threshold.compareTo(min) < 0)) {
            throw unprocessable("threshold for %s must be %s %s..%s".formatted(type, minExclusive ? "over" : "from",
                    min.toPlainString(), max.toPlainString()));
        }
    }

    private static GeneralUnprocessableEntityException unprocessable(final String message) {
        return new GeneralUnprocessableEntityException(ExceptionErrorCode.ALERT_INVALID, message);
    }

    private AlertModel reload(final String userId, final Long id) {
        final BondAlert alert = find(userId, id);
        // правило без облигации (ключевая ставка) - без подписи
        final Bond bond = alert.getTicker() == null
                ? null
                : bondRepository.findByTickerIn(List.of(alert.getTicker())).stream().findFirst().orElse(null);
        return toModel(alert, bond);
    }

    private BondAlert find(final String userId, final Long id) {
        return alertRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.NOT_FOUND,
                        NOT_FOUND.formatted(id)));
    }

    private AlertModel toModel(final BondAlert alert, final Bond bond) {
        return new AlertModel(alert.getId(), alert.getTicker(),
                bond == null ? null : bond.getName(),
                bond == null ? null : bond.getIsin(),
                alert.getType(), alert.getDays(), alert.getDirection(), alert.getThreshold(), alert.getBaseValue(),
                alert.getState(), alert.isEnabled(), alert.getCreatedAt(), alert.getLastTriggeredAt());
    }

    private NotificationModel toModel(final Notification notification) {
        return new NotificationModel(notification.getId(), notification.getAlertId(), notification.getTicker(),
                notification.getText(), notification.getStatus(), notification.getAttempts(),
                notification.getCreatedAt(), notification.getSentAt(), notification.getLastError());
    }
}
