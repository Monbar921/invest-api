package ru.invest.api.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.Coupon;
import ru.invest.api.common.entity.Price;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.repository.CouponRepository;
import ru.invest.api.common.repository.PriceRepository;
import ru.invest.api.common.usecase.CouponSyncUseCase;
import ru.invest.api.common.usecase.PriceSyncUseCase;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffMoneyApiMapper;
import ru.invest.api.tinkoff.supplier.wrapper.InstrumentsGrpcRateLimitedWrapper;
import ru.invest.api.tinkoff.supplier.wrapper.MarketDataGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsResponse;
import ru.tinkoff.piapi.contract.v1.GetLastPricesResponse;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Quotation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static ru.invest.api.common.constants.SchedulerConstants.SCHEDULER_PROCESS;

public class PriceSyncTest extends AbstractInvestApplicationTest {
    private static final String TICKER = "RU000A10EQ34";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    // цена облигации в процентах от номинала, номинал в фикстуре - 1000 rub
    private static final BigDecimal PRICE_PERCENTAGE = new BigDecimal("98.5");
    private static final BigDecimal EXPECTED_CURRENT_PRICE = new BigDecimal("985.00");

    @Autowired
    private PriceSyncUseCase priceSyncUseCase;
    @Autowired
    private CouponSyncUseCase couponSyncUseCase;
    @Autowired
    private AuditMapper auditMapper;
    @Autowired
    private DateTimeMapper dateTimeMapper;
    @Autowired
    private TinkoffMoneyApiMapper tinkoffMoneyApiMapper;
    @Autowired
    private BondRepository bondRepository;
    @Autowired
    private PriceRepository priceRepository;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private InstrumentsGrpcRateLimitedWrapper instrumentsGrpcWrapper;
    @Autowired
    private MarketDataGrpcRateLimitedWrapper marketDataGrpcWrapper;

    private Bond bond;
    private AuditModel audit;

    @BeforeEach
    @Sql("/sql/clean-up.sql")
    public void setUp() {
        bond = bondRepository.findByTicker(TICKER).orElseThrow();
        audit = auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS);

        // доходность считается по выплатам купонов, поэтому сначала загружаем их
        when(instrumentsGrpcWrapper.getBondCoupons(any())).thenReturn(loadCouponFixture());
        couponSyncUseCase.syncByTicker(TICKER, audit);

        when(marketDataGrpcWrapper.getLastPrices(any())).thenReturn(GetLastPricesResponse.newBuilder()
                .addLastPrices(LastPrice.newBuilder()
                        .setInstrumentUid(bond.getUid())
                        .setPrice(Quotation.newBuilder()
                                .setUnits(PRICE_PERCENTAGE.longValue())
                                .setNano(PRICE_PERCENTAGE.remainder(BigDecimal.ONE).movePointRight(9).intValueExact())))
                .build());
    }

    @Test
    @Sql(scripts = "/sql/bonds-and-coupons-RU000A10EQ34.sql")
    public void syncAllPricesTest() {
        priceSyncUseCase.syncAll(audit);

        assertPriceAndInterestSynced();
    }

    @Test
    @Sql(scripts = "/sql/bonds-and-coupons-RU000A10EQ34.sql")
    public void syncByTickerPricesTest() {
        priceSyncUseCase.syncByTicker(TICKER, audit);

        assertPriceAndInterestSynced();
    }

    private void assertPriceAndInterestSynced() {
        final Price actualPrice = priceRepository.findByUid(bond.getUid()).orElseThrow();

        assertThat(actualPrice.getPrice(), comparesEqualTo(EXPECTED_CURRENT_PRICE));
        assertThat(actualPrice.getCurrency(), equalTo(actualPrice.getNominalCurrency()));
        assertThat(actualPrice.getUpdated(), notNullValue());
        assertThat(actualPrice.getUpdated().getCommittedBy(), equalTo(SCHEDULER_PROCESS));

        final Coupon actualCoupon = couponRepository.findByUid(bond.getUid()).orElseThrow();

        // numeric(19,2) в БД округляет значение половиной вверх
        assertThat(actualCoupon.getInterest(), comparesEqualTo(expectedInterest(actualCoupon).setScale(2, RoundingMode.HALF_UP)));
        assertThat(actualCoupon.getUpdated(), notNullValue());
        assertThat(actualCoupon.getUpdated().getCommittedBy(), equalTo(SCHEDULER_PROCESS));
    }

    /**
     * Годовая доходность: сумма ближайших quantityPerYear будущих выплат из фикстуры, делённая на текущую цену.
     * Считается от текущей даты так же, как в сервисе, чтобы тест не устаревал вместе с фикстурой.
     */
    private BigDecimal expectedInterest(final Coupon coupon) {
        final GetBondCouponsResponse fixture = loadCouponFixture();
        final LocalDateTime now = dateTimeMapper.getNow();

        final BigDecimal yearPayments = fixture.getEventsList()
                .stream()
                .filter(event -> dateTimeMapper.toLocalDateTime(event.getCouponDate()).isAfter(now))
                .sorted(Comparator.comparing(event -> dateTimeMapper.toLocalDateTime(event.getCouponDate())))
                .limit(coupon.getQuantityPerYear())
                .map(event -> tinkoffMoneyApiMapper.toModel(event.getPayOneBond()).getQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return yearPayments
                .multiply(HUNDRED)
                .divide(EXPECTED_CURRENT_PRICE, 10, RoundingMode.FLOOR);
    }
}
