package ru.invest.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class InvestApplication {
    public static void main(String[] args) {
        SpringApplication.run(InvestApplication.class, args);
    }
}
