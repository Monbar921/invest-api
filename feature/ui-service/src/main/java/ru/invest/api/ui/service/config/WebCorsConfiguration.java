package ru.invest.api.ui.service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * UI ходит в API напрямую из браузера. Открыты только эндпоинты облигаций - maintenance UI не вызывает.
 */
@Configuration
@RequiredArgsConstructor
public class WebCorsConfiguration implements WebMvcConfigurer {
    private final CorsProperties corsProperties;

    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        registry.addMapping("/internal/rest/bonds/**")
                .allowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods(HttpMethod.GET.name(), HttpMethod.POST.name());
    }
}
