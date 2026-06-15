package ru.invest.api.bond.supplier.usecase.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;

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

    @Test
    void getForeignCurrencyBonds_shouldReturnAll_whenBatchLimitIsNull() {
        final List<BondModel> result = bondUseCase.getForeignCurrencyBonds(null);
        assertEquals(5, result.size());
    }

    @Test
    void getForeignCurrencyBonds_shouldReturnAll_whenParametersModelIsNull() {
        final List<BondModel> result = bondUseCase.getForeignCurrencyBonds(new BondParametersModel());
        assertEquals(5, result.size());
    }

    @Test
    void getForeignCurrencyBonds_shouldLimit_whenBatchLimitProvided() {
        final BondParametersModel params = new BondParametersModel().setBatchLimit(3);
        final List<BondModel> result = bondUseCase.getForeignCurrencyBonds(params);
        assertEquals(3, result.size());
    }

    @Test
    void getForeignCurrencyBonds_shouldReturnAll_whenBatchLimitExceedsSize() {
        final BondParametersModel params = new BondParametersModel().setBatchLimit(100);
        final List<BondModel> result = bondUseCase.getForeignCurrencyBonds(params);
        assertEquals(5, result.size());
    }

    @Test
    void getForeignCurrencyBonds_shouldReturnEmpty_whenNoBondsAvailable() {
        when(tinkoffBondUseCase.getForeignCurrencyBonds()).thenReturn(List.of());
        final List<BondModel> result = bondUseCase.getForeignCurrencyBonds(null);
        assertTrue(result.isEmpty());
    }
}
