package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.invest.api.common.entity.Audit;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.Coupon;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.model.AmortizationModel;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.repository.CouponRepository;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffCouponMapper;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffPriceEntityMapper;
import ru.invest.api.tinkoff.supplier.service.CouponCalculationService;
import ru.invest.api.tinkoff.supplier.service.YieldMetrics;
import ru.invest.api.tinkoff.supplier.usecase.CouponInterestUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffAmortizationUseCase;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponInterestUseCaseImpl implements CouponInterestUseCase {
    private static final int BATCH_SIZE = 500;

    private final CouponRepository couponRepository;
    private final TinkoffCouponMapper tinkoffCouponMapper;
    private final TinkoffPriceEntityMapper tinkoffPriceEntityMapper;
    private final CouponCalculationService couponCalculationService;
    private final TinkoffAmortizationUseCase tinkoffAmortizationUseCase;
    private final AuditMapper auditMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void recalculate(final Set<String> uids, final AuditModel audit) {
        if (CollectionUtils.isEmpty(uids)) {
            return;
        }

        // каждая пачка в своей транзакции: ошибка в одной пачке не откатывает уже посчитанные
        ListUtils.partition(List.copyOf(uids), BATCH_SIZE)
                .forEach(batch -> transactionTemplate.executeWithoutResult(
                        status -> recalculateBatch(new HashSet<>(batch), audit)));
    }

    private void recalculate(final Coupon coupon, final List<AmortizationModel> amortizations, final Audit updated) {
        // расчёту нужны цена, даты погашения и оферты и график амортизации облигации, остальное берётся из купона
        final Bond bond = coupon.getBond();
        final BondModel bondModel = new BondModel()
                .setPrice(tinkoffPriceEntityMapper.toModel(bond.getPrice()))
                .setMaturityDate(bond.getMaturityDate())
                .setOfferDate(bond.getOfferDate())
                .setAmortizationFlag(bond.isAmortizationFlag())
                .setAmortizations(amortizations);
        final CouponModel couponModel = tinkoffCouponMapper.toModel(coupon);

        final YieldMetrics maturity = couponCalculationService.calculateMaturityMetrics(couponModel, bondModel);
        coupon.setInterest(couponCalculationService.calculateInterest(couponModel, bondModel))
                .setYieldToMaturity(maturity.yield())
                .setDurationMacaulay(maturity.durationMacaulay())
                .setDurationModified(maturity.durationModified())
                .setYieldToOffer(couponCalculationService.calculateYieldToOffer(couponModel, bondModel))
                .setUpdated(updated);
    }

    private void recalculateBatch(final Set<String> uids, final AuditModel audit) {
        final List<Coupon> coupons = couponRepository.findWithPriceAndCouponDataByUidIn(uids);
        final Map<String, List<AmortizationModel>> amortizations = tinkoffAmortizationUseCase.findByUids(uids);
        final Audit updated = auditMapper.toEntity(audit);

        coupons.stream()
                .filter(Objects::nonNull)
                .forEach(coupon -> recalculate(coupon, amortizations.getOrDefault(coupon.getUid(), List.of()), updated));

        couponRepository.saveAll(coupons);
    }
}
