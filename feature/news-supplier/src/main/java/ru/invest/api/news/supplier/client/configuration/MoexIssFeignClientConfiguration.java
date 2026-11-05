package ru.invest.api.news.supplier.client.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.invest.api.news.supplier.client.feign.MoexIssClient;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "spring.cloud.openfeign.client.config.moex-iss-client",
        name = "enabled",
        havingValue = "true"
)
public class MoexIssFeignClientConfiguration {
    private static final String CLIENT_NAME = "moex-iss-client";

    @Value("${spring.cloud.openfeign.client.config.moex-iss-client.url}")
    private String moexIssClientUrl;

    @Bean
    @ConditionalOnMissingBean
    public MoexIssClient moexIssClient(final ApplicationContext applicationContext) {
        return new FeignClientBuilder(applicationContext)
                .forType(MoexIssClient.class, CLIENT_NAME)
                .url(moexIssClientUrl)
                .build();
    }
}
