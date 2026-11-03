package ru.invest.api.ui.service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.data.domain.Page;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.dto.bond.BondDto;
import ru.invest.api.dto.bond.PriceDto;
import ru.invest.api.dto.page.PageDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Mapper(uses = DateTimeMapper.class)
public interface BondDtoMapper {
    BondDto toDto(BondModel bond);

    List<BondDto> toDto(List<BondModel> bonds);

    @Mapping(target = "currentInRub", source = "currentInRub", qualifiedByName = "toKopecks")
    PriceDto toDto(PriceModel price);

    default PageDto<BondDto> toDto(final Page<BondModel> page) {
        return new PageDto<BondDto>()
                .setContent(toDto(page.getContent()))
                .setPage(page.getNumber())
                .setSize(page.getSize())
                .setTotalElements(page.getTotalElements())
                .setTotalPages(page.getTotalPages());
    }

    // сортировка идёт по точному значению, наружу - до копеек
    @Named("toKopecks")
    default BigDecimal toKopecks(final BigDecimal rubles) {
        return rubles == null ? null : rubles.setScale(2, RoundingMode.HALF_UP);
    }
}
