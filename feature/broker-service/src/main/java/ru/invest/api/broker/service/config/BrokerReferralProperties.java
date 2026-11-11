package ru.invest.api.broker.service.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Брокеры-партнёры для блока «Купить у брокера». Пустой список - блок не показывается.
 * Ошибка в настройке партнёра (нет id, имени, рекламодателя, шаблон не https) не даёт приложению стартовать.
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "ru.invest.api.broker-referral")
public class BrokerReferralProperties {
    /** Часовой пояс дня, за который считаются переходы */
    @NotNull
    private ZoneId zone = ZoneId.of("Europe/Moscow");

    @Valid
    private List<Partner> partners = new ArrayList<>();

    @Data
    public static class Partner {
        /** Постоянный id: по нему считаются переходы, он стоит в адресе клика. Не менять - статистика разойдётся */
        @NotBlank
        @Pattern(regexp = "[a-z0-9-]{1,64}", message = "id: латиница в нижнем регистре, цифры и дефис, до 64 символов")
        private String id;

        /** Название брокера на кнопке */
        @NotBlank
        private String name;

        /**
         * Шаблон ссылки партнёрской программы, только https. Подстановки (кодируются для URL): {ticker}, {isin},
         * {erid}.
         */
        @NotBlank
        @Pattern(regexp = "https://\\S+", message = "url-template: адрес https без пробелов")
        private String urlTemplate;

        /** Рекламодатель для маркировки: наименование и ИНН, например «АО "Брокер", ИНН 7700000000» */
        @NotBlank
        private String advertiser;

        /** Токен рекламы из ЕРИР (erid); пусто - не показывается, в {erid} подставляется пустая строка */
        private String erid;

        /** false - партнёр остаётся в настройке, но не показывается и клики по нему не принимаются */
        private boolean enabled = true;
    }
}
