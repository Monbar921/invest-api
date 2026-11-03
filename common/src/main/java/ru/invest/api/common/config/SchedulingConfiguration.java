package ru.invest.api.common.config;

import javax.sql.DataSource;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT60S", defaultLockAtLeastFor = "PT30S")
@EnableScheduling
public class SchedulingConfiguration {
    /**
     * Блокировки @SchedulerLock в таблице shedlock (liquibase/006-db.shedlock.xml), схема берётся из search_path.
     * Время - по часам БД: у нескольких экземпляров приложения часы могут расходиться.
     */
    @Bean
    public LockProvider lockProvider(final DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
