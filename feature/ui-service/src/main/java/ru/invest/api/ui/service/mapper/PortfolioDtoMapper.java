package ru.invest.api.ui.service.mapper;

import org.mapstruct.Mapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.dto.portfolio.BrokerDto;
import ru.invest.api.dto.portfolio.PortfolioDto;
import ru.invest.api.dto.portfolio.PortfolioGroupDto;
import ru.invest.api.dto.portfolio.PortfolioPositionDto;
import ru.invest.api.dto.portfolio.PortfolioPositionRequest;
import ru.invest.api.portfolio.service.model.BrokerModel;
import ru.invest.api.portfolio.service.model.PortfolioGroupModel;
import ru.invest.api.portfolio.service.model.PortfolioModel;
import ru.invest.api.portfolio.service.model.PositionModel;
import ru.invest.api.portfolio.service.model.PositionRequestModel;

import java.util.List;

@Mapper(uses = DateTimeMapper.class)
public interface PortfolioDtoMapper {
    PortfolioDto toDto(PortfolioModel portfolio);

    PortfolioPositionDto toDto(PositionModel position);

    PortfolioGroupDto toDto(PortfolioGroupModel group);

    List<BrokerDto> toBrokerDto(List<BrokerModel> brokers);

    PositionRequestModel toModel(PortfolioPositionRequest request);
}
