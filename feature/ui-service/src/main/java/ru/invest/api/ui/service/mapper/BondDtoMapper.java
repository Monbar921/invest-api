package ru.invest.api.ui.service.mapper;

import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.dto.bond.BondDto;
import ru.invest.api.dto.page.PageDto;

import java.util.List;

@Mapper(uses = DateTimeMapper.class)
public interface BondDtoMapper {
    BondDto toDto(BondModel bond);

    List<BondDto> toDto(List<BondModel> bonds);

    default PageDto<BondDto> toDto(final Page<BondModel> page) {
        return new PageDto<BondDto>()
                .setContent(toDto(page.getContent()))
                .setPage(page.getNumber())
                .setSize(page.getSize())
                .setTotalElements(page.getTotalElements())
                .setTotalPages(page.getTotalPages());
    }
}
