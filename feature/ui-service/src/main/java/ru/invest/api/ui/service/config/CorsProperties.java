package ru.invest.api.ui.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Адреса UI, которым браузер разрешает ходить в API облигаций с другого origin.
 * Пустой список - кросс-доменные запросы запрещены.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ru.invest.api.cors")
public class CorsProperties {
    private List<String> allowedOrigins = new ArrayList<>();
}
