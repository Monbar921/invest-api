package ru.invest.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class InvestApplication {
    public static void main(String[] args) {
        SpringApplication.run(InvestApplication.class, args);
    }
}
