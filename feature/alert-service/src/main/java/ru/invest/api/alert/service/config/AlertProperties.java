package ru.invest.api.alert.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Уведомления по облигациям: ограничения правил, расписание проверки, очередь отправки.
 */
@Data
@Component
@SuppressWarnings("checkstyle:MagicNumber")
@ConfigurationProperties(prefix = "ru.invest.api.alerts")
public class AlertProperties {
    /** Сколько правил может быть у пользователя */
    private int maxAlertsPerUser = 50;

    /** Самое раннее предупреждение о событии, дней */
    private int maxDays = 60;

    /** Часовой пояс дат событий и «сегодня» */
    private ZoneId zone = ZoneId.of("Europe/Moscow");

    /** Сколько повторять отправку, прежде чем уведомление станет FAILED */
    private Duration giveUpAfter = Duration.ofHours(24);

    /** Сколько хранить отправленные и неотправленные уведомления */
    private Duration retention = Duration.ofDays(90);

    /**
     * Проверять правила по цене и доходности после каждого обновления цен. false - только из тестов, которые
     * проверяют на заданную дату сами и не хотят побочной проверки от синхронизации цен в других тестах.
     */
    private boolean priceCheckOnSync = true;

    /** Проверять правила по новостям, рейтингам и ключевой ставке после каждой загрузки ленты; false - в тестах */
    private boolean newsCheckOnSync = true;

    private Dispatcher dispatcher = new Dispatcher();

    @Data
    public static class Dispatcher {
        /**
         * false - очередь не разбирается по расписанию и сразу после срабатывания правила (тесты разбирают сами).
         */
        private boolean enabled = true;

        /** Пауза между разборами очереди */
        private Duration interval = Duration.ofSeconds(30);

        /** Сколько уведомлений отправлять за один разбор */
        private int batchSize = 50;
    }
}
