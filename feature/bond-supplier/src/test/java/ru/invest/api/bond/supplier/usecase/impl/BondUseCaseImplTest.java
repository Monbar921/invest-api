package ru.invest.api.bond.supplier.usecase.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.enums.RiskLevel;
import ru.invest.api.common.model.parameters.BondSortField;
import ru.invest.api.common.model.parameters.BondSortOrder;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BondUseCaseImplTest {

    @Mock
    private TinkoffBondUseCase tinkoffBondUseCase;

    @InjectMocks
    private BondUseCaseImpl bondUseCase;

    private List<BondModel> bonds;

    @BeforeEach
    void setUp() {
        bonds = List.of(new BondModel(), new BondModel(), new BondModel(), new BondModel(), new BondModel());
        when(tinkoffBondUseCase.getForeignCurrencyBonds()).thenReturn(bonds);
    }

    // ─── batchLimit ───────────────────────────────────────────────────────────

    @Test
    void shouldReturnAll_whenParamsNull() {
        assertEquals(5, bondUseCase.getForeignCurrencyBonds(null).size());
    }

    @Test
    void shouldReturnAll_whenBatchLimitAbsent() {
        assertEquals(5, bondUseCase.getForeignCurrencyBonds(new BondParametersModel()).size());
    }

    @Test
    void shouldLimit_whenBatchLimitProvided() {
        final BondParametersModel params = new BondParametersModel().setBatchLimit(3);
        assertEquals(3, bondUseCase.getForeignCurrencyBonds(params).size());
    }

    @Test
    void shouldReturnAll_whenBatchLimitExceedsSize() {
        final BondParametersModel params = new BondParametersModel().setBatchLimit(100);
        assertEquals(5, bondUseCase.getForeignCurrencyBonds(params).size());
    }

    @Test
    void shouldReturnEmpty_whenNoBonds() {
        when(tinkoffBondUseCase.getForeignCurrencyBonds()).thenReturn(List.of());
        assertTrue(bondUseCase.getForeignCurrencyBonds(null).isEmpty());
    }

    // ─── TICKER sort ──────────────────────────────────────────────────────────

    @Test
    void shouldSortByTickerAsc() {
        when(tinkoffBondUseCase.getForeignCurrencyBonds()).thenReturn(List.of(
                new BondModel().setTicker("C"),
                new BondModel().setTicker("A"),
                new BondModel().setTicker("B")
        ));

        final BondParametersModel params = new BondParametersModel()
                .setSortField(BondSortField.TICKER)
                .setSortOrder(BondSortOrder.ASC);

        final List<BondModel> result = bondUseCase.getForeignCurrencyBonds(params);
        assertEquals("A", result.get(0).getTicker());
        assertEquals("B", result.get(1).getTicker());
        assertEquals("C", result.get(2).getTicker());
    }

    @Test
    void shouldSortByTickerDesc() {
        when(tinkoffBondUseCase.getForeignCurrencyBonds()).thenReturn(List.of(
                new BondModel().setTicker("A"),
                new BondModel().setTicker("C"),
                new BondModel().setTicker("B")
        ));

        final BondParametersModel params = new BondParametersModel()
                .setSortField(BondSortField.TICKER)
                .setSortOrder(BondSortOrder.DESC);

        final List<BondModel> result = bondUseCase.getForeignCurrencyBonds(params);
        assertEquals("C", result.get(0).getTicker());
        assertEquals("B", result.get(1).getTicker());
        assertEquals("A", result.get(2).getTicker());
    }

    // ─── COUPON_INTEREST sort ─────────────────────────────────────────────────

    @Test
    void shouldSortByCouponInterestAsc() {
        when(tinkoffBondUseCase.getForeignCurrencyBonds()).thenReturn(List.of(
                bondWithInterest("HIGH", new BigDecimal("8.5")),
                bondWithInterest("LOW", new BigDecimal("3.0")),
                bondWithInterest("MID", new BigDecimal("5.5"))
        ));

        final BondParametersModel params = new BondParametersModel()
                .setSortField(BondSortField.COUPON_INTEREST)
                .setSortOrder(BondSortOrder.ASC);

        final List<BondModel> result = bondUseCase.getForeignCurrencyBonds(params);
        assertEquals("LOW", result.get(0).getTicker());
        assertEquals("MID", result.get(1).getTicker());
        assertEquals("HIGH", result.get(2).getTicker());
    }

    // ─── RISK_LEVEL sort ──────────────────────────────────────────────────────

    @Test
    void shouldSortByRiskLevelAsc_lowBeforeHigh() {
        when(tinkoffBondUseCase.getForeignCurrencyBonds()).thenReturn(List.of(
                new BondModel().setTicker("HIGH").setRiskLevel(RiskLevel.RISK_LEVEL_HIGH),
                new BondModel().setTicker("LOW").setRiskLevel(RiskLevel.RISK_LEVEL_LOW),
                new BondModel().setTicker("MOD").setRiskLevel(RiskLevel.RISK_LEVEL_MODERATE)
        ));

        final BondParametersModel params = new BondParametersModel()
                .setSortField(BondSortField.RISK_LEVEL)
                .setSortOrder(BondSortOrder.ASC);

        final List<BondModel> result = bondUseCase.getForeignCurrencyBonds(params);
        // LOW и MODERATE оба в приоритете 0, HIGH → 1
        assertEquals(RiskLevel.RISK_LEVEL_HIGH, result.get(2).getRiskLevel());
    }

    // ─── MATURITY_DATE sort ───────────────────────────────────────────────────

    @Test
    void shouldSortByMaturityDateAsc() {
        final LocalDateTime date1 = LocalDateTime.of(2025, 1, 1, 0, 0);
        final LocalDateTime date2 = LocalDateTime.of(2027, 1, 1, 0, 0);
        final LocalDateTime date3 = LocalDateTime.of(2026, 1, 1, 0, 0);

        when(tinkoffBondUseCase.getForeignCurrencyBonds()).thenReturn(List.of(
                new BondModel().setTicker("FAR").setMaturityDate(date2),
                new BondModel().setTicker("NEAR").setMaturityDate(date1),
                new BondModel().setTicker("MID").setMaturityDate(date3)
        ));

        final BondParametersModel params = new BondParametersModel()
                .setSortField(BondSortField.MATURITY_DATE)
                .setSortOrder(BondSortOrder.ASC);

        final List<BondModel> result = bondUseCase.getForeignCurrencyBonds(params);
        assertEquals("NEAR", result.get(0).getTicker());
        assertEquals("MID", result.get(1).getTicker());
        assertEquals("FAR", result.get(2).getTicker());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private BondModel bondWithInterest(final String ticker, final BigDecimal interest) {
        return new BondModel()
                .setTicker(ticker)
                .setCoupon(new CouponModel().setInterest(interest));
    }
}
