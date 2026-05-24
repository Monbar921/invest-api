package ru.invest.api.starter.annotation;

import org.springframework.context.annotation.Import;
import ru.invest.api.starter.autoconfiguration.InvestApiClientAutoConfiguration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(InvestApiClientAutoConfiguration.class)
public @interface EnableInvestApiClient {
    String value() default "invest-api-client";
}
