package ru.invest.api.common.usecase;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParametersModel;

import java.util.List;

public interface BondSortUseCase {
    List<BondModel> getFilteredBonds(BondParametersModel bondParameters, List<BondModel> bonds);
}
