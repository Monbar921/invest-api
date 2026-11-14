package ru.invest.api.alert.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.alert.service.model.SavedFilterChangeModel;
import ru.invest.api.alert.service.model.SavedFilterModel;
import ru.invest.api.alert.service.usecase.FilterAlertCheckUseCase;
import ru.invest.api.alert.service.usecase.SavedFilterUseCase;
import ru.invest.api.bond.supplier.model.BondScope;
import ru.invest.api.common.entity.BondAlert;
import ru.invest.api.common.entity.SavedFilter;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.account.PlanLimit;
import ru.invest.api.common.model.alert.AlertType;
import ru.invest.api.common.repository.BondAlertRepository;
import ru.invest.api.common.repository.SavedFilterMatchRepository;
import ru.invest.api.common.repository.SavedFilterRepository;
import ru.invest.api.common.usecase.EntitlementUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheRepositoryUseCase;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Сохранённые фильтры. Уведомления по фильтру - правило bond_alert типа SAVED_FILTER: оно занимает место в лимите
 * правил, выключается кнопкой в Telegram как любое правило и удаляется вместе с фильтром.
 */
@Service
@RequiredArgsConstructor
public class SavedFilterUseCaseImpl implements SavedFilterUseCase {
    private static final String NOT_FOUND = "Saved filter %d not found";

    private final SavedFilterRepository filterRepository;
    private final SavedFilterMatchRepository matchRepository;
    private final BondAlertRepository alertRepository;
    private final SavedFilterParameters parameters;
    private final FilterAlertCheckUseCase filterAlertCheckUseCase;
    private final EntitlementUseCase entitlementUseCase;
    private final TinkoffBondCacheRepositoryUseCase bondCache;

    @Override
    @Transactional(readOnly = true)
    public List<SavedFilterModel> getFilters(final String userId) {
        final List<SavedFilter> filters = filterRepository.findByUserIdOrderByNameAscIdAsc(userId);
        final Map<Long, BondAlert> alerts = filters.isEmpty()
                ? Map.of()
                : alertRepository.findByFilterIdIn(filters.stream().map(SavedFilter::getId).toList()).stream()
                .collect(Collectors.toMap(BondAlert::getFilterId, Function.identity(), (first, second) -> first));
        return filters.stream().map(filter -> toModel(filter, alerts.get(filter.getId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SavedFilterModel getFilter(final String userId, final Long id) {
        final SavedFilter filter = find(userId, id);
        return toModel(filter, alertRepository.findByFilterId(id).orElse(null));
    }

    @Override
    @Transactional
    public SavedFilterModel create(final String userId, final SavedFilterChangeModel change) {
        final String name = name(change.name());
        if (filterRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw duplicate(name);
        }
        // 402, если фильтров уже столько, сколько разрешает тариф
        entitlementUseCase.requireBelowLimit(userId, PlanLimit.SAVED_FILTERS, filterRepository.countByUserId(userId));
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final SavedFilter filter = filterRepository.save(new SavedFilter()
                .setUserId(userId)
                .setName(name)
                .setScope((change.scope() == null ? BondScope.ALL : change.scope()).name())
                .setParameters(parameters.write(change.parameters()))
                .setCreatedAt(now));
        if (Boolean.TRUE.equals(change.notifications())) {
            enableNotifications(userId, filter, now);
        }
        return getFilter(userId, filter.getId());
    }

    @Override
    @Transactional
    public SavedFilterModel update(final String userId, final Long id, final SavedFilterChangeModel change) {
        final SavedFilter filter = find(userId, id);
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (change.name() != null) {
            final String name = name(change.name());
            if (filterRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, id)) {
                throw duplicate(name);
            }
            filter.setName(name);
        }
        boolean selectionChanged = false;
        if (change.scope() != null && !change.scope().name().equals(filter.getScope())) {
            filter.setScope(change.scope().name());
            selectionChanged = true;
        }
        if (change.parameters() != null) {
            final String json = parameters.write(change.parameters());
            selectionChanged |= !json.equals(filter.getParameters());
            filter.setParameters(json);
        }
        filterRepository.save(filter.setUpdatedAt(now));

        final BondAlert alert = alertRepository.findByFilterId(id).orElse(null);
        if (Boolean.FALSE.equals(change.notifications())) {
            if (alert != null) {
                alertRepository.delete(alert);
                matchRepository.deleteByFilterId(id);
            }
        } else if (Boolean.TRUE.equals(change.notifications()) && (alert == null || !alert.isEnabled())) {
            enableNotifications(userId, filter, now);
        } else if (selectionChanged && alert != null && alert.isEnabled()) {
            // другой фильтр - другая выборка: отправная точка заново, без уведомлений обо всём, что подошло сейчас
            alert.setState(null);
            alertRepository.save(alert);
            filterAlertCheckUseCase.check(alert.getId());
        }
        return getFilter(userId, id);
    }

    @Override
    @Transactional
    public void delete(final String userId, final Long id) {
        final SavedFilter filter = find(userId, id);
        alertRepository.findByFilterId(id).ifPresent(alertRepository::delete);
        matchRepository.deleteByFilterId(id);
        filterRepository.delete(filter);
    }

    /**
     * Правило есть (выключено, например, кнопкой в Telegram) - включается; нет - создаётся в пределах лимита правил.
     * Текущая выборка - отправная точка.
     */
    private void enableNotifications(final String userId, final SavedFilter filter, final LocalDateTime now) {
        final BondAlert alert = alertRepository.findByFilterId(filter.getId()).orElseGet(() -> {
            entitlementUseCase.requireBelowLimit(userId, PlanLimit.ALERTS, alertRepository.countByUserId(userId));
            return new BondAlert()
                    .setUserId(userId)
                    .setType(AlertType.SAVED_FILTER)
                    .setFilterId(filter.getId())
                    .setCreatedAt(now);
        });
        alert.setEnabled(true).setState(null);
        filterAlertCheckUseCase.check(alertRepository.save(alert).getId());
    }

    private SavedFilter find(final String userId, final Long id) {
        return filterRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.NOT_FOUND,
                        NOT_FOUND.formatted(id)));
    }

    private static String name(final String name) {
        if (name == null || name.isBlank()) {
            throw new GeneralUnprocessableEntityException(ExceptionErrorCode.ALERT_INVALID, "name must not be blank");
        }
        return name.strip();
    }

    private static GeneralUnprocessableEntityException duplicate(final String name) {
        return new GeneralUnprocessableEntityException(ExceptionErrorCode.ALERT_INVALID,
                "Saved filter \"%s\" already exists".formatted(name));
    }

    private SavedFilterModel toModel(final SavedFilter filter, final BondAlert alert) {
        final BondScope scope = SavedFilterParameters.scope(filter);
        final Integer matches = bondCache.getBonds().isEmpty()
                ? null
                : parameters.select(scope, parameters.read(filter)).size();
        return new SavedFilterModel(filter.getId(), filter.getName(), scope, parameters.read(filter),
                alert != null && alert.isEnabled(), alert == null ? null : alert.getId(), matches,
                filter.getCreatedAt(), Objects.requireNonNullElse(filter.getUpdatedAt(), filter.getCreatedAt()));
    }
}
