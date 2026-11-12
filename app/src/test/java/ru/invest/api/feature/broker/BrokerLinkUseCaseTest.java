package ru.invest.api.feature.broker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.broker.service.config.BrokerReferralProperties;
import ru.invest.api.broker.service.config.BrokerReferralProperties.Partner;
import ru.invest.api.broker.service.model.BrokerLinkModel;
import ru.invest.api.broker.service.usecase.impl.BrokerLinkUseCaseImpl;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.repository.BrokerClickRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Сборка ссылки по шаблону и день клика без Spring и БД: облигации, счётчик и «сейчас» - моки.
 */
class BrokerLinkUseCaseTest {
    private static final String TICKER = "SU26238RMFS4";
    private static final String ISIN = "RU000A1038V6";
    // 2026-11-11 22:30 UTC - в Москве уже 2026-11-12 01:30
    private static final LocalDateTime LATE_EVENING_UTC = LocalDateTime.of(2026, 11, 11, 22, 30);

    private final BondUseCase bondUseCase = mock(BondUseCase.class);
    private final BrokerClickRepository brokerClickRepository = mock(BrokerClickRepository.class);
    private final DateTimeMapper dateTimeMapper = mock(DateTimeMapper.class);
    private final BrokerReferralProperties properties = new BrokerReferralProperties();
    private final BrokerLinkUseCaseImpl useCase =
            new BrokerLinkUseCaseImpl(properties, bondUseCase, brokerClickRepository, dateTimeMapper);

    @BeforeEach
    void setUp() {
        bondWith(TICKER, ISIN);
    }

    @Test
    void placeholdersAreUrlEncoded() {
        final String ticker = "A&B=C/D?Е#";
        bondWith(ticker, "RU 000+A1");
        properties.setPartners(List.of(partner("enc", "https://enc.test/b/{ticker}?isin={isin}&erid={erid}",
                " 2SDnj&erid=x ")));

        final BrokerLinkModel link = useCase.getLinks(ticker).getFirst();

        assertThat(link.url(), equalTo("https://enc.test/b/A%26B%3DC%2FD%3F%D0%95%23?isin=RU+000%2BA1&erid=2SDnj%26erid%3Dx"));
        // в маркировке erid показывается как есть, только без пробелов по краям
        assertThat(link.erid(), equalTo("2SDnj&erid=x"));
    }

    @Test
    void templateWithoutPlaceholdersIsReturnedUnchanged() {
        final String url = "https://tbank-online.com/baf/9LgoeKcxhP";
        properties.setPartners(List.of(partner("tbank", url, "2SDnjTbank")));

        assertThat(useCase.getLinks(TICKER).getFirst().url(), equalTo(url));
    }

    @Test
    void blankEridIsNullAndEmptyInTemplate() {
        properties.setPartners(List.of(partner("blank", "https://blank.test/{isin}?erid={erid}", "   ")));

        final BrokerLinkModel link = useCase.getLinks(TICKER).getFirst();

        assertThat(link.url(), equalTo("https://blank.test/" + ISIN + "?erid="));
        assertThat(link.erid(), nullValue());
    }

    @Test
    void onlyDisabledPartnersGiveNoLinksWithoutBondLookup() {
        final Partner disabled = partner("off", "https://off.test/{ticker}", null);
        disabled.setEnabled(false);
        properties.setPartners(List.of(disabled));

        assertThat(useCase.getLinks("UNKNOWN"), empty());
        verifyNoInteractions(bondUseCase);
    }

    @Test
    void clickDayIsTakenInConfiguredZone() {
        properties.setPartners(List.of(partner("alpha", "https://alpha.test/{ticker}", null)));
        when(dateTimeMapper.getNow()).thenReturn(LATE_EVENING_UTC);

        useCase.registerClick(TICKER, "alpha");
        verify(brokerClickRepository).increment(LocalDate.of(2026, 11, 12), "alpha", TICKER);

        properties.setZone(ZoneId.of("UTC"));
        useCase.registerClick(TICKER, "alpha");
        verify(brokerClickRepository).increment(LocalDate.of(2026, 11, 11), "alpha", TICKER);
    }

    private void bondWith(final String ticker, final String isin) {
        when(bondUseCase.getByTickers(any())).thenReturn(Map.of(ticker, new BondModel().setTicker(ticker).setIsin(isin)));
    }

    private static Partner partner(final String id, final String urlTemplate, final String erid) {
        final Partner partner = new Partner();
        partner.setId(id);
        partner.setName("Брокер " + id);
        partner.setUrlTemplate(urlTemplate);
        partner.setAdvertiser("ООО \"Брокер\", ИНН 7700000000");
        partner.setErid(erid);
        return partner;
    }
}
