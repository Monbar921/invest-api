package ru.invest.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.invest.api.common.model.BondModel;
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
        final List<BondModel> bonds = bondUseCase.getForeignCurrencyBonds();
        assertThat(!bonds.isEmpty(), is(true));
    }
}
