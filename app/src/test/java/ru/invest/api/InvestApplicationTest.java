package ru.invest.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import ru.invest.api.instrument.usecase.InstrumentUseCase;
import ru.invest.api.share.usecase.ShareUseCase;

@SpringBootTest
public class InvestApplicationTest {
    @Autowired
    private ShareUseCase shareUseCase;

    @Test
    public void plain(){
        shareUseCase.getCommonSharePrice("MOEX");
    }
}
