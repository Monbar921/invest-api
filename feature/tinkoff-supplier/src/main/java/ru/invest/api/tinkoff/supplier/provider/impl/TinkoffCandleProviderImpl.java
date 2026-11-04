package ru.invest.api.tinkoff.supplier.provider.impl;

import com.google.protobuf.Timestamp;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.mapper.BigDecimalMapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.candle.CandleModel;
import ru.invest.api.tinkoff.supplier.provider.TinkoffCandleProvider;
import ru.invest.api.tinkoff.supplier.wrapper.MarketDataGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.CandleInterval;
import ru.tinkoff.piapi.contract.v1.GetCandlesRequest;
import ru.tinkoff.piapi.contract.v1.GetCandlesRequest.CandleSource;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;
import ru.tinkoff.piapi.contract.v1.Quotation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static ru.invest.api.common.exception.enums.ExceptionErrorCode.EMPTY_UID;

@Service
@RequiredArgsConstructor
public class TinkoffCandleProviderImpl implements TinkoffCandleProvider {
    /**
     * Дневные свечи API отдаёт окнами до 6 лет и не больше 2400 за запрос; 5 лет - с запасом по обоим ограничениям.
     */
    private static final int WINDOW_YEARS = 5;
    /** Сколько окон назад искать историю - 30 лет, дольше облигации не торгуются */
    private static final int MAX_HISTORY_WINDOWS = 6;

    private final MarketDataGrpcRateLimitedWrapper marketDataGrpcRateLimitedWrapper;
    private final BigDecimalMapper bigDecimalMapper;
    private final DateTimeMapper dateTimeMapper;

    @Override
    public List<CandleModel> getDailyCandles(final String uid, final LocalDate from, final LocalDate to) {
        validate(uid);
        final List<CandleModel> candles = new ArrayList<>();
        // окна [start, start + 5 лет), последнее - до конца дня to
        for (LocalDate start = from; !start.isAfter(to); start = start.plusYears(WINDOW_YEARS)) {
            final LocalDate end = min(start.plusYears(WINDOW_YEARS), to.plusDays(1));
            candles.addAll(fetch(uid, start, end));
        }
        return sorted(candles);
    }

    @Override
    public List<CandleModel> getDailyHistory(final String uid, final LocalDate to) {
        validate(uid);
        final List<CandleModel> candles = new ArrayList<>();
        LocalDate end = to.plusDays(1);
        for (int window = 0; window < MAX_HISTORY_WINDOWS; window++) {
            final LocalDate start = end.minusYears(WINDOW_YEARS);
            final List<CandleModel> windowCandles = fetch(uid, start, end);
            // глубина истории в API не описана: идём назад, пока окно не окажется пустым
            if (windowCandles.isEmpty()) {
                break;
            }
            candles.addAll(windowCandles);
            end = start;
        }
        return sorted(candles);
    }

    private List<CandleModel> fetch(final String uid, final LocalDate from, final LocalDate toExclusive) {
        final GetCandlesRequest request = GetCandlesRequest.newBuilder()
                .setInstrumentId(uid)
                .setInterval(CandleInterval.CANDLE_INTERVAL_DAY)
                .setFrom(toTimestamp(from))
                .setTo(toTimestamp(toExclusive))
                // только биржевые свечи: торги выходного дня - не биржевая цена.
                // limit не задаём: вместе с источником свечей API отвечает INVALID_ARGUMENT 30220,
                // а в окно 5 лет и так помещается меньше 2400 дневных свечей
                .setCandleSourceType(CandleSource.CANDLE_SOURCE_EXCHANGE)
                .build();

        return marketDataGrpcRateLimitedWrapper.getCandles(request)
                .getCandlesList()
                .stream()
                .map(this::toModel)
                .toList();
    }

    private CandleModel toModel(final HistoricCandle candle) {
        return new CandleModel()
                .setDate(dateTimeMapper.toLocalDateTime(candle.getTime()).toLocalDate())
                .setOpen(toBigDecimal(candle.getOpen()))
                .setHigh(toBigDecimal(candle.getHigh()))
                .setLow(toBigDecimal(candle.getLow()))
                .setClose(toBigDecimal(candle.getClose()))
                .setVolume(candle.getVolume());
    }

    private BigDecimal toBigDecimal(final Quotation quotation) {
        return bigDecimalMapper.fromBaseAndNanoFloatParts(quotation.getUnits(), quotation.getNano());
    }

    // окна могут пересечься на границе - одна свеча на день, по возрастанию даты
    private List<CandleModel> sorted(final List<CandleModel> candles) {
        final Map<LocalDate, CandleModel> byDate = new TreeMap<>();
        candles.forEach(candle -> byDate.put(candle.getDate(), candle));
        return byDate.values().stream().sorted(Comparator.comparing(CandleModel::getDate)).toList();
    }

    private void validate(final String uid) {
        if (StringUtils.isEmpty(uid)) {
            throw new GeneralUnprocessableEntityException(EMPTY_UID, "Provide uid for getting candles");
        }
    }

    private static LocalDate min(final LocalDate first, final LocalDate second) {
        return first.isBefore(second) ? first : second;
    }

    private static Timestamp toTimestamp(final LocalDate date) {
        return Timestamp.newBuilder()
                .setSeconds(date.atStartOfDay().toEpochSecond(ZoneOffset.UTC))
                .build();
    }
}
