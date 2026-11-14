package ru.invest.api.news.supplier.screener;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.entity.BondCouponBase;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.repository.BondCouponBaseRepository;
import ru.invest.api.common.usecase.BondScreenerEnricher;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * База флоатера - описание купона Мосбиржи («Ключевая ставка», «Ставка RUONIA», «ИПЦ») у облигаций с переменным
 * купоном. Премию к базе биржа не публикует, другого бесплатного источника нет - она остаётся null.
 */
@Service
@RequiredArgsConstructor
public class FloaterBaseRateEnricher implements BondScreenerEnricher {
    // описание фиксированного купона («Фиксированный с известными купонами») - не база ставки
    private static final String FIXED_PREFIX = "фиксирован";

    private final BondCouponBaseRepository bondCouponBaseRepository;

    @Override
    public void enrich(final Collection<BondModel> bonds, final boolean detailed) {
        final Set<String> isins = bonds.stream()
                .filter(Objects::nonNull)
                .filter(bond -> bond.getCoupon() != null && Boolean.FALSE.equals(bond.getCoupon().getIsFixed()))
                .map(BondModel::getIsin)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        if (isins.isEmpty()) {
            return;
        }
        final Map<String, String> bases = bondCouponBaseRepository.findByIsinIn(isins).stream()
                .filter(base -> StringUtils.isNotBlank(base.getCouponDetails()))
                .filter(base -> !base.getCouponDetails().toLowerCase(Locale.ROOT).startsWith(FIXED_PREFIX))
                .collect(Collectors.toMap(BondCouponBase::getIsin, BondCouponBase::getCouponDetails));

        bonds.stream()
                .filter(Objects::nonNull)
                .filter(bond -> isins.contains(bond.getIsin()))
                .forEach(bond -> bond.setFloaterBaseRate(bases.get(bond.getIsin())));
    }
}
