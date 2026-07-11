package ru.invest.api.common.usecase;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondSortModel;

import java.util.Comparator;
import java.util.List;

public interface BondComparatorUseCase {
    Comparator<BondModel> createComparator(List<BondSortModel> bondSorts);
}
