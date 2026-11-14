package ru.invest.api.alert.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.invest.api.bond.supplier.model.BondScope;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.entity.SavedFilter;
import ru.invest.api.common.mapper.BondParametersMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParametersRequestModel;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Параметры сохранённого фильтра: JSON в БД и выборка по ним - тот же фильтр и сортировка, что у списка облигаций.
 */
@Component
@RequiredArgsConstructor
class SavedFilterParameters {
    private final JsonMapper jsonMapper;
    private final BondParametersMapper bondParametersMapper;
    private final BondUseCase bondUseCase;

    String write(final BondParametersRequestModel parameters) {
        final BondParametersRequestModel value = parameters == null ? new BondParametersRequestModel() : parameters;
        // размер выборки к фильтру не относится
        value.setBatchLimit(null);
        return jsonMapper.writeValueAsString(value);
    }

    /** Каждый раз новый объект - его можно менять */
    BondParametersRequestModel read(final SavedFilter filter) {
        return jsonMapper.readValue(filter.getParameters(), BondParametersRequestModel.class);
    }

    static BondScope scope(final SavedFilter filter) {
        return BondScope.valueOf(filter.getScope());
    }

    /** Выборка по фильтру в порядке его сортировки */
    List<BondModel> select(final BondScope scope, final BondParametersRequestModel parameters) {
        return bondUseCase.getAllFiltered(scope, bondParametersMapper.toModel(parameters));
    }
}
