package ru.invest.api.tinkoff.supplier.mapper;

import com.google.protobuf.Timestamp;
import lombok.Setter;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.AmortizationModel;
import ru.invest.api.common.model.AuditModel;
import ru.tinkoff.piapi.contract.v1.GetBondEventsResponse.BondEvent;

import java.time.LocalDateTime;

@Mapper
@SuppressWarnings("checkstyle:AbstractClassName")
public abstract class TinkoffAmortizationApiMapper {
    @Setter(onMethod_ = @Autowired)
    private TinkoffMoneyApiMapper tinkoffMoneyApiMapper;
    @Setter(onMethod_ = @Autowired)
    private DateTimeMapper dateTimeMapper;

    public AmortizationModel toModel(final BondEvent event, final String ticker, final String uid, final AuditModel logged) {
        if (event == null) {
            return null;
        }

        return new AmortizationModel()
                .setTicker(ticker)
                .setUid(uid)
                .setLogged(logged)
                .setPrice(event.hasPayOneBond() ? tinkoffMoneyApiMapper.toModel(event.getPayOneBond()) : null)
                .setPaymentDate(getPaymentDate(event));
    }

    // дата выплаты заполнена не всегда - тогда берём дату события
    private LocalDateTime getPaymentDate(final BondEvent event) {
        final Timestamp date = isSet(event.getPayDate()) ? event.getPayDate() : event.getEventDate();
        return isSet(date) ? dateTimeMapper.toLocalDateTime(date) : null;
    }

    private boolean isSet(final Timestamp timestamp) {
        return timestamp != null && timestamp.getSeconds() > 0;
    }
}
