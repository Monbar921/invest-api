package ru.invest.api.ui.service.mapper;

import org.mapstruct.Mapper;
import ru.invest.api.bond.supplier.collection.BondCollection;
import ru.invest.api.common.model.news.IssuerPageModel;
import ru.invest.api.dto.bond.collection.BondCollectionDto;
import ru.invest.api.dto.bond.issuer.IssuerDto;
import ru.invest.api.dto.bond.issuer.IssuerShortDto;

import java.util.List;

/**
 * Эмитенты и подборки облигаций.
 */
@Mapper(uses = BondDtoMapper.class)
public interface IssuerDtoMapper {
    IssuerDto toDto(IssuerPageModel issuer);

    IssuerShortDto toShortDto(IssuerPageModel issuer);

    BondCollectionDto toCollectionDto(BondCollection collection);

    List<BondCollectionDto> toCollectionDto(List<BondCollection> collections);
}
