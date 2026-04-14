package ru.invest.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.invest.api.stock.supplier.usecase.BondUseCase;

@SpringBootTest
public class InvestApplicationTest {
    @Autowired
    private BondUseCase bondUseCase;

    @Test
    public void plain() {
        bondUseCase.getForeignCurrencyBonds();
    }
}
