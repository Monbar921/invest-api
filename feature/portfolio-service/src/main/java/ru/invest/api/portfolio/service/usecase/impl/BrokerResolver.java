package ru.invest.api.portfolio.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.invest.api.common.entity.PortfolioBroker;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.repository.PortfolioBrokerRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Брокеры пользователя - метки покупок и продаж.
 */
@Component
@RequiredArgsConstructor
class BrokerResolver {
    private static final int MAX_BROKERS = 50;

    private final PortfolioBrokerRepository brokerRepository;

    /**
     * Брокер по имени без учёта регистра и пробелов по краям; такого у пользователя ещё нет - создаётся и дальше
     * доступен для других сделок. Пустое имя - без брокера.
     */
    PortfolioBroker resolve(final String userId, final String name) {
        final String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return brokerRepository.findByUserIdAndNameIgnoreCase(userId, trimmed).orElseGet(() -> {
            if (brokerRepository.countByUserId(userId) >= MAX_BROKERS) {
                throw new GeneralUnprocessableEntityException(ExceptionErrorCode.PORTFOLIO_INVALID,
                        "No more than %d brokers per user".formatted(MAX_BROKERS));
            }
            return brokerRepository.save(new PortfolioBroker()
                    .setUserId(userId)
                    .setName(trimmed)
                    .setCreatedAt(LocalDateTime.now(ZoneOffset.UTC)));
        });
    }

    /** Имена брокеров пользователя по id */
    Map<Long, String> names(final String userId) {
        return brokerRepository.findByUserIdOrderByNameAsc(userId).stream()
                .collect(Collectors.toMap(PortfolioBroker::getId, PortfolioBroker::getName));
    }
}
