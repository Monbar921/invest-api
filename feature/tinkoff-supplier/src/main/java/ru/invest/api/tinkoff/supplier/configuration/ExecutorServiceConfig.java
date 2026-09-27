package ru.invest.api.tinkoff.supplier.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static ru.invest.api.tinkoff.supplier.constants.Constants.COUPON_EXECUTOR_SERVICE;

@Configuration
public class ExecutorServiceConfig {
    private static final int COUPON_EXECUTOR_SERVICE_POOL_SIZE = 10;

    @Bean(COUPON_EXECUTOR_SERVICE)
    public ExecutorService couponExecutorService() {
        return Executors.newFixedThreadPool(COUPON_EXECUTOR_SERVICE_POOL_SIZE);
    }
}
