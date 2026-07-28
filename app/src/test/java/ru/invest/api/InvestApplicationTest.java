package ru.invest.api;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.enums.RiskLevel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.common.model.parameters.BondSortField;
import ru.invest.api.common.model.parameters.BondSortModel;
import ru.invest.api.common.model.parameters.BondSortOrder;
import ru.invest.api.common.model.parameters.ValueRangeModel;
import ru.invest.api.dto.bond.BondDto;
import ru.invest.api.starter.client.InvestApiBondClient;
import ru.invest.api.tinkoff.supplier.dispatcher.TinkoffBondDispatcher;
import ru.invest.api.tinkoff.supplier.usecase.BondSyncUseCase;
import ru.invest.api.tinkoff.supplier.usecase.CouponSyncUseCase;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class InvestApplicationTest extends AbstractInvestApplicationTest {
//    @MockitoBean
//    private BudgetOrgClient budgetOrgClient;
//    @MockitoBean
//    private CbRfClient cbRfClient;
//    @MockitoBean
//    private InstrumentsServiceGrpc.InstrumentsServiceBlockingStub instrumentsServiceBlockingStub;
//    @MockitoBean
//    private MarketDataServiceGrpc.MarketDataServiceBlockingStub marketDataServiceBlockingStub;

    @Autowired
    private TinkoffBondDispatcher tinkoffBondDispatcher;
    @Autowired
    private BondUseCase bondUseCase;
    @Autowired
    private InvestApiBondClient investApiBondClient;
    @Autowired
    private BondSyncUseCase bondSyncUseCase;
    @Autowired
    private CouponSyncUseCase couponSyncUseCase;

    @Test
    public void runApplicationTest() {
    }

    @Test
    @Disabled
    public void realCall() {
//        final List<BondModel> bonds = tinkoffBondUseCase.getForeignCurrencyBonds();
        final List<BondModel> bonds = bondUseCase.getForeignCurrencyBonds(getBondParameters());
        assertThat(!bonds.isEmpty(), is(true));

        bonds
                .forEach(bond -> {
                    System.out.println("name=%40s, ticker=%20s, coupon=%6s%%, price=%6s%%"
                                    .formatted(bond.getName(), bond.getTicker(), bond.getCoupon().getInterest(), bond.getPrice().getPercentagePrice())
                    );
                });
    }

    @Test
    @Disabled
    public void sync() {
        bondSyncUseCase.syncAll();
        couponSyncUseCase.syncAll();
    }

    @Test
    @Disabled
    public void clientCall() {
        final List<BondDto> bonds = investApiBondClient.getAllForeignBonds(null, Collections.emptyList());
        assertThat(!bonds.isEmpty(), is(true));
    }

    private BondParametersModel getBondParameters() {
        final ValueRangeModel currentPriceRange = new ValueRangeModel()
                .setMax(BigDecimal.valueOf(100000L));
        final ValueRangeModel percentagePriceRange = new ValueRangeModel()
                .setMax(BigDecimal.valueOf(102L));

        final BondSortModel couponSort = new BondSortModel()
                .setSortField(BondSortField.COUPON_INTEREST)
                .setSortOrder(BondSortOrder.DESC);

        final BondSortModel riscLevelSort = new BondSortModel()
                .setSortField(BondSortField.RISK_LEVEL)
                .setSortOrder(BondSortOrder.ASC);

        final List<RiskLevel> riskLevels = List.of(RiskLevel.RISK_LEVEL_MODERATE, RiskLevel.RISK_LEVEL_LOW);

        final List<BondSortModel> sorts = List.of(couponSort, riscLevelSort);

        return new BondParametersModel()
                .setCurrentPrice(currentPriceRange)
                .setPercentagePrice(percentagePriceRange)
                .setBondSorts(sorts)
                .setRiskLevels(riskLevels)
                .setIsOfz(false);
    }
}
