package ru.invest.api.tinkoff.supplier.provider.impl;

import com.google.protobuf.Timestamp;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.AmortizationModel;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffAmortizationApiMapper;
import ru.invest.api.tinkoff.supplier.provider.TinkoffAmortizationProvider;
import ru.invest.api.tinkoff.supplier.wrapper.InstrumentsGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.GetBondEventsRequest;
import ru.tinkoff.piapi.contract.v1.GetBondEventsRequest.EventType;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

import static ru.invest.api.common.exception.enums.ExceptionErrorCode.EMPTY_TICKER;
import static ru.invest.api.common.exception.enums.ExceptionErrorCode.EMPTY_UID;
import static ru.invest.api.tinkoff.supplier.constants.Constants.BOND_SCHEDULE_HORIZON_YEARS;
import static ru.invest.api.tinkoff.supplier.constants.Constants.PAST_COUPONS_YEARS;

@Service
@RequiredArgsConstructor
public class TinkoffAmortizationProviderImpl implements TinkoffAmortizationProvider {
    private final TinkoffAmortizationApiMapper tinkoffAmortizationApiMapper;
    private final InstrumentsGrpcRateLimitedWrapper instrumentsGrpcRateLimitedWrapper;
    private final DateTimeMapper dateTimeMapper;

    @Override
    public List<AmortizationModel> getAmortizations(final String uid, final String ticker, final AuditModel audit) {
        if (StringUtils.isEmpty(uid)) {
            throw new GeneralUnprocessableEntityException(EMPTY_UID, "Provide uid for getting amortization info");
        }
        if (StringUtils.isEmpty(ticker)) {
            throw new GeneralUnprocessableEntityException(EMPTY_TICKER, "Provide ticker for getting amortization info");
        }

        final LocalDateTime now = dateTimeMapper.getNow();
        final GetBondEventsRequest request = GetBondEventsRequest.newBuilder()
                .setInstrumentId(uid)
                .setType(EventType.EVENT_TYPE_MTY)
                // прошлые погашения - на ту же глубину, что и купоны: по ним портфель считает полученную амортизацию
                // и номинал на дату покупки; расчёты доходности берут только будущие
                .setFrom(toTimestamp(now.minusYears(PAST_COUPONS_YEARS)))
                .setTo(toTimestamp(now.plusYears(BOND_SCHEDULE_HORIZON_YEARS)))
                .build();

        return instrumentsGrpcRateLimitedWrapper.getBondEvents(request)
                .getEventsList()
                .stream()
                .filter(Objects::nonNull)
                .map(event -> tinkoffAmortizationApiMapper.toModel(event, ticker, uid, audit))
                .filter(Objects::nonNull)
                .filter(amortization -> amortization.getPaymentDate() != null)
                // размер выплаты ещё не объявлен - расчёт доходности отнесёт эту часть номинала на дату погашения
                .filter(amortization -> isPositive(amortization.getPrice()))
                .toList();
    }

    private boolean isPositive(final MoneyModel money) {
        return money != null
                && StringUtils.isNotBlank(money.getCurrency())
                && money.getQuantity() != null
                && money.getQuantity().signum() > 0;
    }

    private Timestamp toTimestamp(final LocalDateTime dateTime) {
        return Timestamp.newBuilder()
                .setSeconds(dateTime.toEpochSecond(ZoneOffset.UTC))
                .build();
    }
}
