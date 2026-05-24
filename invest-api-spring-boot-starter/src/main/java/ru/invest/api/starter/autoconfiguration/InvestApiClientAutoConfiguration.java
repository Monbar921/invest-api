package ru.invest.api.starter.autoconfiguration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnBean(annotation = aero.s7.smi.hotels.starter.annotation.EnableInvestApiClient.class)
public class InvestApiClientAutoConfiguration extends AbstractClientAutoConfiguration<aero.s7.smi.hotels.starter.annotation.EnableInvestApiClient> {
    public InvestApiClientAutoConfiguration(final Environment environment,
                                            final ApplicationContext applicationContext) {
        super(environment, applicationContext, aero.s7.smi.hotels.starter.annotation.EnableInvestApiClient.class);
    }

    @Bean
    @ConditionalOnMissingBean
    public aero.s7.smi.hotels.starter.client.InvestApiClient sbaHotelsClient() {
        final String clientName = getClientName();
        return new FeignClientBuilder(getApplicationContext())
                .forType(
                        aero.s7.smi.hotels.starter.client.InvestApiClient.class,
                        clientName
                )
                .url(getClientUrl(clientName))
                .path("/internal/rest")
                .build();
    }
}
