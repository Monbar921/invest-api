package ru.invest.api.alert.service.usecase.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.invest.api.alert.service.config.AlertProperties;
import ru.invest.api.alert.service.usecase.FilterAlertCheckUseCase;
import ru.invest.api.bond.supplier.model.BondScope;
import ru.invest.api.common.entity.BondAlert;
import ru.invest.api.common.entity.SavedFilter;
import ru.invest.api.common.entity.SavedFilterMatch;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.alert.AlertState;
import ru.invest.api.common.model.alert.AlertType;
import ru.invest.api.common.repository.BondAlertRepository;
import ru.invest.api.common.repository.SavedFilterMatchRepository;
import ru.invest.api.common.repository.SavedFilterRepository;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheRepositoryUseCase;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Новые облигации в выборке сохранённого фильтра. Выборка прошлой проверки - в saved_filter_match.
 * <p>
 * Правило без отправной точки (state не ARMED: только что включено или изменён фильтр) запоминает текущую выборку и
 * ничего не шлёт - уведомляем только о том, что войдёт в выборку потом. Дальше на каждой проверке: вошедшие в выборку
 * облигации, которых не было в прошлой, - одно уведомление со списком (не больше filter-max-bonds строк); из прошлой
 * выборки облигация уходит, только когда не проходит фильтр и с запасом у границ ({@link FilterMargins}).
 */
@Slf4j
@Service
public class FilterAlertCheckUseCaseImpl implements FilterAlertCheckUseCase {
    private final BondAlertRepository alertRepository;
    private final SavedFilterRepository filterRepository;
    private final SavedFilterMatchRepository matchRepository;
    private final SavedFilterParameters parameters;
    private final TinkoffBondCacheRepositoryUseCase bondCache;
    private final NotificationQueue notificationQueue;
    private final AlertProperties properties;
    private final TransactionTemplate transaction;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public FilterAlertCheckUseCaseImpl(final BondAlertRepository alertRepository,
                                       final SavedFilterRepository filterRepository,
                                       final SavedFilterMatchRepository matchRepository,
                                       final SavedFilterParameters parameters,
                                       final TinkoffBondCacheRepositoryUseCase bondCache,
                                       final NotificationQueue notificationQueue,
                                       final AlertProperties properties,
                                       final PlatformTransactionManager transactionManager) {
        this.alertRepository = alertRepository;
        this.filterRepository = filterRepository;
        this.matchRepository = matchRepository;
        this.parameters = parameters;
        this.bondCache = bondCache;
        this.notificationQueue = notificationQueue;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public int checkAll() {
        final List<Long> ids = alertRepository.findByEnabledTrueAndTypeIn(Set.of(AlertType.SAVED_FILTER)).stream()
                .map(BondAlert::getId)
                .toList();
        if (ids.isEmpty() || !bondsLoaded()) {
            return 0;
        }
        int queued = 0;
        for (final Long id : ids) {
            try {
                queued += checkInTransaction(id);
            } catch (final RuntimeException e) {
                log.error("Saved filter alert check failed for alert {}", id, e);
            }
        }
        log.info("Saved filter alert check: {} rules, {} notifications queued", ids.size(), queued);
        return queued;
    }

    @Override
    public int check(final Long alertId) {
        // пустой кэш (облигации ещё не загружены) - пустая выборка: отправной точкой она быть не может
        return bondsLoaded() ? checkInTransaction(alertId) : 0;
    }

    private int checkInTransaction(final Long alertId) {
        return Optional.ofNullable(transaction.execute(status -> alertRepository.findById(alertId)
                .filter(BondAlert::isEnabled)
                .filter(alert -> alert.getType() == AlertType.SAVED_FILTER && alert.getFilterId() != null)
                .flatMap(alert -> filterRepository.findById(alert.getFilterId())
                        .map(filter -> checkFilter(alert, filter)))
                .orElse(0))).orElse(0);
    }

    private int checkFilter(final BondAlert alert, final SavedFilter filter) {
        final BondScope scope = SavedFilterParameters.scope(filter);
        final List<BondModel> selection = parameters.select(scope, parameters.read(filter));
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (alert.getState() != AlertState.ARMED) {
            matchRepository.deleteByFilterId(filter.getId());
            matchRepository.saveAll(selection.stream().map(bond -> match(filter, bond, now)).toList());
            alert.setState(AlertState.ARMED);
            alertRepository.save(alert);
            return 0;
        }

        final List<SavedFilterMatch> previous = matchRepository.findByFilterId(filter.getId());
        final Set<String> previousUids = previous.stream().map(SavedFilterMatch::getUid).collect(Collectors.toSet());
        final Set<String> strictUids = selection.stream().map(BondModel::getUid).collect(Collectors.toSet());
        final Set<String> looseUids = parameters.select(scope, FilterMargins.loosen(parameters.read(filter))).stream()
                .map(BondModel::getUid)
                .collect(Collectors.toSet());

        // вышла из выборки и с запасом (или облигации больше нет) - при новом входе будет новое уведомление
        matchRepository.deleteAll(previous.stream()
                .filter(match -> !strictUids.contains(match.getUid()) && !looseUids.contains(match.getUid()))
                .toList());
        final List<BondModel> entered = selection.stream()
                .filter(bond -> !previousUids.contains(bond.getUid()))
                .toList();
        if (entered.isEmpty()) {
            return 0;
        }
        matchRepository.saveAll(entered.stream().map(bond -> match(filter, bond, now)).toList());
        final String dedupKey = "%s:%d:%d".formatted(alert.getType(), alert.getId(), alert.getTriggerCount() + 1);
        if (!notificationQueue.enqueue(alert.getUserId(), alert.getId(), null, dedupKey,
                AlertTexts.filter(filter.getName(), entered, properties.getFilterMaxBonds()))) {
            return 0;
        }
        alert.setTriggerCount(alert.getTriggerCount() + 1);
        alert.setLastTriggeredAt(now);
        alertRepository.save(alert);
        return 1;
    }

    private boolean bondsLoaded() {
        return !bondCache.getBonds().isEmpty();
    }

    private static SavedFilterMatch match(final SavedFilter filter, final BondModel bond, final LocalDateTime now) {
        return new SavedFilterMatch()
                .setFilterId(filter.getId())
                .setUid(bond.getUid())
                .setTicker(bond.getTicker())
                .setMatchedAt(now);
    }
}
