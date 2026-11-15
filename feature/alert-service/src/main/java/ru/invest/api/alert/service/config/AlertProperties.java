package ru.invest.api.alert.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
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

    /** Сколько новых облигаций перечислять в одном уведомлении по сохранённому фильтру; остальные - числом */
    private int filterMaxBonds = 10;

    private Dispatcher dispatcher = new Dispatcher();

    private Risk risk = new Risk();

    private Digest digest = new Digest();

    /** Монитор риска по портфелю */
    @Data
    public static class Risk {
        /**
         * Сколько дней одно и то же событие (риск того же вида по тому же эмитенту) не повторяется: о дефолте пишут
         * биржа, агентства и несколько СМИ, иногда на следующий день
         */
        private int quietDays = 3;

        /** Больше уведомлений по одному правилу за проверку не шлём (первая загрузка ленты, сбой) */
        private int maxPerCheck = 5;

        /** Сколько новых новостей просматривать за проверку; остальные - в следующую */
        private int scanLimit = 1000;
    }

    /** Дайджест портфеля */
    @Data
    public static class Digest {
        /** День отправки еженедельного дайджеста; ежедневный - каждый день */
        private DayOfWeek weeklyDay = DayOfWeek.MONDAY;

        /** Сколько выплат перечислять; остальные - числом */
        private int maxPayments = 5;

        /** Сколько новостей перечислять; остальные - числом */
        private int maxNews = 5;
    }

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
