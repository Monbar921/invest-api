package ru.invest.api.common.config;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT60S", defaultLockAtLeastFor = "PT30S")
@EnableScheduling
public class SchedulingConfiguration {
}
