package ru.invest.api.starter.autoconfiguration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import ru.invest.api.starter.annotation.EnableInvestApiBondClient;
import ru.invest.api.starter.client.InvestApiBondClient;

@Configuration
@ConditionalOnBean(annotation = EnableInvestApiBondClient.class)
public class InvestApiBondClientAutoConfiguration extends AbstractClientAutoConfiguration<EnableInvestApiBondClient> {
    public InvestApiBondClientAutoConfiguration(final Environment environment,
                                                final ApplicationContext applicationContext) {
        super(environment, applicationContext, EnableInvestApiBondClient.class);
    }

    @Bean
    @ConditionalOnMissingBean
    public InvestApiBondClient investApiBondClient() {
        final String clientName = getClientName();
        return new FeignClientBuilder(getApplicationContext())
                .forType(
                        InvestApiBondClient.class,
                        clientName
                )
                .url(getClientUrl(clientName))
                .path("/internal/rest")
                .build();
    }
}
