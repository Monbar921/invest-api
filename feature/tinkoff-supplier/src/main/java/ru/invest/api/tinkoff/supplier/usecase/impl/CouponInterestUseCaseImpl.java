package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.invest.api.common.entity.Audit;
import ru.invest.api.common.entity.Coupon;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.repository.CouponRepository;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffCouponMapper;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffPriceEntityMapper;
import ru.invest.api.tinkoff.supplier.service.CouponCalculationService;
import ru.invest.api.tinkoff.supplier.usecase.CouponInterestUseCase;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
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

    private void recalculate(final Coupon coupon, final Audit updated) {
        // расчёту нужна только текущая цена облигации, остальное берётся из купона
        final BondModel bondModel = new BondModel()
                .setPrice(tinkoffPriceEntityMapper.toModel(coupon.getBond().getPrice()));

        final BigDecimal interest = couponCalculationService.calculateInterest(tinkoffCouponMapper.toModel(coupon), bondModel);

        coupon.setInterest(interest)
                .setUpdated(updated);
    }

    private void recalculateBatch(final Set<String> uids, final AuditModel audit) {
        final List<Coupon> coupons = couponRepository.findWithPriceAndCouponDataByUidIn(uids);
        final Audit updated = auditMapper.toEntity(audit);

        coupons.stream()
                .filter(Objects::nonNull)
                .forEach(coupon -> recalculate(coupon, updated));

        couponRepository.saveAll(coupons);
    }
}
