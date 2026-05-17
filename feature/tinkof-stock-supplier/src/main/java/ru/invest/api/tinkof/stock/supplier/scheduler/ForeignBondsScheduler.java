package ru.invest.api.tinkof.stock.supplier.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.invest.api.common.model.parameters.BondParameters;
import ru.invest.api.tinkof.stock.supplier.usecase.BondUseCase;

@Component
@ConditionalOnProperty(
        prefix = "ru.invest.api.stock.supplier.scheduler",
        name = "enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class ForeignBondsScheduler {
    private final BondUseCase bondUseCase;

    @Scheduled(cron = "${ru.invest.api.stock.supplier.scheduler.cron}")
    public void fillForeignCurrencyBondsCache() {
        BondParameters bondParameters = new BondParameters();
        bondUseCase.getForeignCurrencyBonds(bondParameters);
    }
}
