package ru.invest.api.ui.service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.dto.request.bond.BondSortRequest;

import java.util.List;

@Mapper
public interface BondParametersRequestMapper {

    BondParametersModel toModel(Integer batchLimit, List<BondSortRequest> bondSorts);
}
