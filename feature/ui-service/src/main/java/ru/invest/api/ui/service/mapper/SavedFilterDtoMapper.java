package ru.invest.api.ui.service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.invest.api.alert.service.model.SavedFilterModel;
import ru.invest.api.bond.supplier.model.BondScope;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.parameters.BondParametersRequestModel;
import ru.invest.api.dto.filter.BondScopeDto;
import ru.invest.api.dto.filter.SavedFilterDto;
import ru.invest.api.dto.request.bond.BondParametersRequest;

import java.util.List;

@Mapper(uses = DateTimeMapper.class)
public interface SavedFilterDtoMapper {
    @Mapping(target = "notify", source = "notifications")
    SavedFilterDto toDto(SavedFilterModel filter);

    List<SavedFilterDto> toDto(List<SavedFilterModel> filters);

    BondParametersRequest toRequest(BondParametersRequestModel parameters);

    @Mapping(target = "batchLimit", ignore = true)
    BondParametersRequestModel toModel(BondParametersRequest parameters);

    BondScope toScope(BondScopeDto scope);
}
