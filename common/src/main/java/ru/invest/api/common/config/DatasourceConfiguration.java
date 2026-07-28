package ru.invest.api.common.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

@EnableScheduling
@Configuration
public class DatasourceConfiguration {
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariConfig hikariConfig() {
        return new HikariConfig();
    }

    @Bean
    public DataSource dataSource(final HikariConfig hikariConfig) {
        return new HikariDataSource(hikariConfig);
    }

    @Bean
    CommandLineRunner logDbUrl(DataSource dataSource) {
        return args -> {
            try (var conn = dataSource.getConnection()) {
                System.out.println("=== ACTUAL JDBC URL: " + conn.getMetaData().getURL());
                System.out.println("=== ACTUAL SCHEMA: " + conn.getSchema());
            }
        };
    }
}
