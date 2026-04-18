package ru.invest.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParameters;
import ru.invest.api.stock.supplier.usecase.BondUseCase;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SpringBootTest
public class InvestApplicationTest {
    @Autowired
    private BondUseCase bondUseCase;

    @Test
    public void plain() {
        final BondParameters parameters = new BondParameters().setBatchLimit(10);
        final List<BondModel> bonds = bondUseCase.getForeignCurrencyBonds(parameters);
        assertThat(!bonds.isEmpty(), is(true));
    }
}
