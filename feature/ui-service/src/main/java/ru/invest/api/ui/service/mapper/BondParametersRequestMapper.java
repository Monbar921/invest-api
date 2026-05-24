package ru.invest.api.ui.service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.invest.api.common.model.parameters.BondParametersModel;

@Mapper
public interface BondParametersRequestMapper {
    @Mapping(target = "batchLimit", source = "batchLimit")
    BondParametersModel toModel(Integer batchLimit);
}
