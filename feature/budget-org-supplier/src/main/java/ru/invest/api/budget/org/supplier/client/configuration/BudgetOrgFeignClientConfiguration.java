package ru.invest.api.budget.org.supplier.client.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.invest.api.budget.org.supplier.client.feign.BudgetOrgClient;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "spring.cloud.openfeign.client.config.budget-org-client",
        name = "enabled",
        havingValue = "true"
)
public class BudgetOrgFeignClientConfiguration {
    private final static String CLIENT_NAME = "budget-org-client";

    @Value("${spring.cloud.openfeign.client.config.budget-org-client.url}")
    private String budgetOrgClientUrl;

    @Bean
    @ConditionalOnMissingBean
    public BudgetOrgClient budgetOrgClient(final ApplicationContext applicationContext) {
        return new FeignClientBuilder(applicationContext)
                .forType(
                        BudgetOrgClient.class,
                        CLIENT_NAME
                )
                .url(budgetOrgClientUrl)
                .path("/fiat")
                .build();
    }
}
