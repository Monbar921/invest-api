package ru.invest.api.tinkoff.supplier.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static ru.invest.api.tinkoff.supplier.constants.Constants.COUPON_EXECUTOR_SERVICE;

@Configuration
public class ExecutorServiceConfig {
    @Bean(COUPON_EXECUTOR_SERVICE)
    public ExecutorService couponExecutorService(){
        return Executors.newFixedThreadPool(10);
    }
}
