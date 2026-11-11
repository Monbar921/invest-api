package ru.invest.api.ui.service.mapper;

import org.springframework.stereotype.Component;
import ru.invest.api.broker.service.model.BrokerClickStatModel;
import ru.invest.api.broker.service.model.BrokerLinkModel;
import ru.invest.api.dto.bond.broker.BrokerClickStatDto;
import ru.invest.api.dto.bond.broker.BrokerLinkDto;

import java.util.List;

@Component
public class BrokerLinkDtoMapper {
    public List<BrokerLinkDto> toLinkDto(final List<BrokerLinkModel> links) {
        return links.stream()
                .map(link -> new BrokerLinkDto()
                        .setPartnerId(link.partnerId())
                        .setName(link.name())
                        .setUrl(link.url())
                        .setAdvertiser(link.advertiser())
                        .setErid(link.erid()))
                .toList();
    }

    public List<BrokerClickStatDto> toClickDto(final List<BrokerClickStatModel> clicks) {
        return clicks.stream()
                .map(click -> new BrokerClickStatDto()
                        .setDate(click.date())
                        .setPartnerId(click.partnerId())
                        .setTicker(click.ticker())
                        .setClicks(click.clicks()))
                .toList();
    }
}
