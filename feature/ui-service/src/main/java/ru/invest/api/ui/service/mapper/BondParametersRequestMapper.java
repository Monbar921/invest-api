package ru.invest.api.ui.service.mapper;

import org.mapstruct.Mapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.dto.request.BondParametersRequest;

@Mapper
public interface BondParametersRequestMapper {
    BondParametersModel toModel(BondParametersRequest parametersRequest);
}
