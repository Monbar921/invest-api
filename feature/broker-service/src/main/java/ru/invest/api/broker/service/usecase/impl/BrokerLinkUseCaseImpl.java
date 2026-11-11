package ru.invest.api.broker.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.broker.service.config.BrokerReferralProperties;
import ru.invest.api.broker.service.config.BrokerReferralProperties.Partner;
import ru.invest.api.broker.service.model.BrokerClickStatModel;
import ru.invest.api.broker.service.model.BrokerLinkModel;
import ru.invest.api.broker.service.usecase.BrokerLinkUseCase;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.repository.BrokerClickRepository;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BrokerLinkUseCaseImpl implements BrokerLinkUseCase {
    private static final String BOND_NOT_FOUND_MESSAGE = "Bond not found for ticker %s";
    private static final String PARTNER_NOT_FOUND_MESSAGE = "Broker partner not found: %s";

    private final BrokerReferralProperties properties;
    private final BondUseCase bondUseCase;
    private final BrokerClickRepository brokerClickRepository;
    private final DateTimeMapper dateTimeMapper;

    @Override
    public List<BrokerLinkModel> getLinks(final String ticker) {
        final List<Partner> partners = enabledPartners();
        if (partners.isEmpty()) {
            return List.of();
        }
        final BondModel bond = getBond(ticker);
        return partners.stream()
                .map(partner -> new BrokerLinkModel(partner.getId(), partner.getName(), buildUrl(partner, bond),
                        partner.getAdvertiser(), blankToNull(partner.getErid())))
                .toList();
    }

    @Override
    @Transactional
    public void registerClick(final String ticker, final String partnerId) {
        final Partner partner = enabledPartners().stream()
                .filter(candidate -> candidate.getId().equals(partnerId))
                .findFirst()
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.BROKER_PARTNER_NOT_FOUND,
                        PARTNER_NOT_FOUND_MESSAGE.formatted(partnerId)));
        final BondModel bond = getBond(ticker);
        brokerClickRepository.increment(today(), partner.getId(), bond.getTicker());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BrokerClickStatModel> getClicks(final LocalDate from, final LocalDate to) {
        return brokerClickRepository.findByClickDateBetweenOrderByClickDateAscPartnerIdAscTickerAsc(from, to).stream()
                .map(click -> new BrokerClickStatModel(click.getClickDate(), click.getPartnerId(), click.getTicker(),
                        click.getClicks()))
                .toList();
    }

    private List<Partner> enabledPartners() {
        return properties.getPartners().stream().filter(Partner::isEnabled).toList();
    }

    private BondModel getBond(final String ticker) {
        final BondModel bond = bondUseCase.getByTickers(Set.of(ticker)).get(ticker);
        if (bond == null) {
            throw new GeneralNotFoundEntityException(ExceptionErrorCode.BOND_NOT_FOUND,
                    BOND_NOT_FOUND_MESSAGE.formatted(ticker));
        }
        return bond;
    }

    private LocalDate today() {
        return dateTimeMapper.getNow().atOffset(ZoneOffset.UTC).atZoneSameInstant(properties.getZone()).toLocalDate();
    }

    private static String buildUrl(final Partner partner, final BondModel bond) {
        return partner.getUrlTemplate()
                .replace("{ticker}", encode(bond.getTicker()))
                .replace("{isin}", encode(bond.getIsin()))
                .replace("{erid}", encode(partner.getErid()));
    }

    private static String encode(final String value) {
        return value == null ? "" : URLEncoder.encode(value.trim(), StandardCharsets.UTF_8);
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
