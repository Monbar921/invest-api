package ru.invest.api.cb.rf.currency.client;

import aero.s7.smi.hotels.starter.annotation.EnableSbaHotelsClient;
import aero.s7.smi.hotels.starter.client.SbaHotelsClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class CbRfFeignClientConfiguration {
    public CbRfFeignClientConfiguration(final Environment environment,
                                        final ApplicationContext applicationContext) {
    }

    @Bean
    @ConditionalOnMissingBean
    public SbaHotelsClient sbaHotelsClient() {
        final String clientName = getClientName();
        return new FeignClientBuilder(getApplicationContext())
                .forType(
                        SbaHotelsClient.class,
                        clientName
                )
                .url(getClientUrl(clientName))
                .path("/internal/rest")
                .build();
    }
}
