package ru.invest.api.ui.service.mapper;

import org.mapstruct.Mapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.dto.portfolio.PortfolioDto;
import ru.invest.api.dto.portfolio.PortfolioPositionDto;
import ru.invest.api.dto.portfolio.PortfolioPositionRequest;
import ru.invest.api.portfolio.service.model.PortfolioModel;
import ru.invest.api.portfolio.service.model.PositionModel;
import ru.invest.api.portfolio.service.model.PositionRequestModel;

@Mapper(uses = DateTimeMapper.class)
public interface PortfolioDtoMapper {
    PortfolioDto toDto(PortfolioModel portfolio);

    PortfolioPositionDto toDto(PositionModel position);

    PositionRequestModel toModel(PortfolioPositionRequest request);
}
