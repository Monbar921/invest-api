package ru.invest.api.ui.service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.dto.portfolio.BrokerDto;
import ru.invest.api.dto.portfolio.CalendarLinkDto;
import ru.invest.api.dto.portfolio.PortfolioCalendarDto;
import ru.invest.api.dto.portfolio.PortfolioDto;
import ru.invest.api.dto.portfolio.PortfolioGroupDto;
import ru.invest.api.dto.portfolio.PortfolioPositionDto;
import ru.invest.api.dto.portfolio.PortfolioPositionRequest;
import ru.invest.api.dto.portfolio.PortfolioSaleRequest;
import ru.invest.api.dto.portfolio.PortfolioTradeDto;
import ru.invest.api.portfolio.service.model.BrokerModel;
import ru.invest.api.portfolio.service.model.CalendarLinkModel;
import ru.invest.api.portfolio.service.model.PortfolioCalendarModel;
import ru.invest.api.portfolio.service.model.PortfolioGroupModel;
import ru.invest.api.portfolio.service.model.PortfolioModel;
import ru.invest.api.portfolio.service.model.PositionModel;
import ru.invest.api.portfolio.service.model.PositionRequestModel;
import ru.invest.api.portfolio.service.model.SaleRequestModel;
import ru.invest.api.portfolio.service.model.TradeModel;

import java.util.List;

@Mapper(uses = DateTimeMapper.class)
public interface PortfolioDtoMapper {
    PortfolioDto toDto(PortfolioModel portfolio);

    PortfolioPositionDto toDto(PositionModel position);

    PortfolioGroupDto toDto(PortfolioGroupModel group);

    PortfolioTradeDto toDto(TradeModel trade);

    List<BrokerDto> toBrokerDto(List<BrokerModel> brokers);

    PositionRequestModel toModel(PortfolioPositionRequest request);

    SaleRequestModel toModel(PortfolioSaleRequest request);

    List<PortfolioTradeDto> toTradeDto(List<TradeModel> trades);

    PortfolioCalendarDto toCalendarDto(PortfolioCalendarModel calendar);

    @Mapping(target = "enabled", expression = "java(link.createdAt() != null)")
    CalendarLinkDto toLinkDto(CalendarLinkModel link);
}
