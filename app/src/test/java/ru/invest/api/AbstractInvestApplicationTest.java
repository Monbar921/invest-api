package ru.invest.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ru.invest.api.config.InvestApiTestConfiguration;

@SpringBootTest(classes = InvestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Import(InvestApiTestConfiguration.class)
public abstract class AbstractInvestApplicationTest {
}
