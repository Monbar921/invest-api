package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.entity.Audit;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.PriceCandle;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.candle.CandleModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.repository.PriceCandleRepository;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCandleUseCase;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TinkoffCandleUseCaseImpl implements TinkoffCandleUseCase {
    private static final String BOND_NOT_FOUND_MESSAGE = "Bond not found for uid %s";

    private final PriceCandleRepository priceCandleRepository;
    private final BondRepository bondRepository;
    private final AuditMapper auditMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDate> findLastCandleDate(final String uid) {
        return priceCandleRepository.findLastCandleDate(uid);
    }

    @Override
    @Transactional
    public void replaceFrom(final String uid, final List<CandleModel> candles, final AuditModel audit) {
        if (CollectionUtils.isEmpty(candles)) {
            return;
        }

        final Bond bond = bondRepository.findByUid(uid)
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.BOND_NOT_FOUND,
                        BOND_NOT_FOUND_MESSAGE.formatted(uid)));
        final Audit created = auditMapper.toEntity(audit);

        priceCandleRepository.deleteFrom(uid, candles.getFirst().getDate());
        // удаление должно дойти до БД раньше вставок, иначе уникальный индекс (uid, дата) не пропустит новую свечу
        priceCandleRepository.flush();
        priceCandleRepository.saveAll(candles.stream()
                .map(candle -> new PriceCandle()
                        .setBond(bond)
                        .setUid(uid)
                        .setCandleDate(candle.getDate())
                        .setOpen(candle.getOpen())
                        .setHigh(candle.getHigh())
                        .setLow(candle.getLow())
                        .setClose(candle.getClose())
                        .setVolume(candle.getVolume())
                        .setCreated(created))
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CandleModel> find(final String uid, final LocalDate from, final LocalDate to) {
        return priceCandleRepository.findByUidAndCandleDateBetweenOrderByCandleDateAsc(uid, from, to)
                .stream()
                .map(candle -> new CandleModel()
                        .setDate(candle.getCandleDate())
                        .setOpen(candle.getOpen())
                        .setHigh(candle.getHigh())
                        .setLow(candle.getLow())
                        .setClose(candle.getClose())
                        .setVolume(candle.getVolume()))
                .toList();
    }
}
