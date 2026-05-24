package ru.invest.api.ui.service.mapper;

import org.mapstruct.Mapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.dto.dto.bond.BondDto;

import java.util.List;

@Mapper(uses = DateTimeMapper.class)
public interface BondDtoMapper {
    BondDto toDto(BondModel bond);

    List<BondDto> toDto(List<BondModel> bonds);
}
