package ru.invest.api.common.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;
import ru.invest.api.common.mapper.BondParametersMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.common.model.parameters.PriceRequestModel;
import ru.invest.api.common.usecase.BondSortUseCase;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BondSortUseCaseImpl implements BondSortUseCase {
    private final BondParametersMapper bondParametersMapper;

    @Override
    public List<BondModel> getFilteredBonds(final BondParametersModel bondParameters, final List<BondModel> bonds) {
        if (CollectionUtils.isEmpty(bonds)) {
            return bonds;
        }

        final BondParametersModel actualizedParameters = bondParametersMapper.toModel(bondParameters);

        return bonds.stream()
                .filter(Objects::nonNull)
                .filter(bond -> filterByPrice(bondParameters.getPrice(), bond))
                .sorted(actualizedParameters.getComparator())
                .limit(actualizedParameters.getBatchLimit())
                .toList();
    }

    private boolean filterByPrice(final PriceRequestModel price, final BondModel bond) {
        if (price == null || ObjectUtils.allNull(price.getMin(), price.getMax())) {
            return true;
        }

        final BigDecimal currentPrice = Optional.ofNullable(bond.getPrice())
                .map(PriceModel::getCurrent)
                .map(MoneyModel::getQuantity)
                .orElse(null);

        if (currentPrice == null) {
            return true;
        }

        return comparePrices(currentPrice, price.getMin(), 1)
                && comparePrices(currentPrice, price.getMax(), -1);
    }

    private boolean comparePrices(final BigDecimal realPrice, final BigDecimal requestedPrice
            , final int conditionMultiplier) {
        if (requestedPrice == null) {
            return true;
        }

        return realPrice.compareTo(requestedPrice) * conditionMultiplier >= 0;
    }
}
