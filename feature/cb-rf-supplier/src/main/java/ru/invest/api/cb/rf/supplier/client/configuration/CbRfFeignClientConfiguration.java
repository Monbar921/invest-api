package ru.invest.api.cb.rf.supplier.client.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.invest.api.cb.rf.supplier.client.feign.CbRfClient;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "spring.cloud.openfeign.client.config.cb-rf-client.enabled",
        name = "enabled",
        havingValue = "true"
)
public class CbRfFeignClientConfiguration {
    private final static String CLIENT_NAME = "cb-rf-client";

    @Value("${spring.cloud.openfeign.client.config.cb-rf-client.url}")
    private String cbRfClientUrl;

    @Bean
    @ConditionalOnMissingBean
    public CbRfClient cbRfClient(final ApplicationContext applicationContext) {
        return new FeignClientBuilder(applicationContext)
                .forType(
                        CbRfClient.class,
                        CLIENT_NAME
                )
                .url(cbRfClientUrl)
                .build();
    }
}
