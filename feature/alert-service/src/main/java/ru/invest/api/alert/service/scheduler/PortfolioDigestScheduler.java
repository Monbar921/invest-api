package ru.invest.api.alert.service.scheduler;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.invest.api.alert.service.config.AlertProperties;
import ru.invest.api.alert.service.usecase.NotificationDispatchUseCase;
import ru.invest.api.alert.service.usecase.PortfolioDigestUseCase;

import java.time.LocalDate;

/**
 * Дайджест портфеля - раз в день (по умолчанию 10:00 МСК, после утренней проверки правил и ночной синхронизации).
 * При старте не запускается: дайджест в момент запуска приложения пришёл бы в случайное время. Пропущенный из-за
 * выключенного приложения дайджест не догоняется - следующий сравнит стоимость с последним отправленным.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.alerts.digest.scheduler", name = "enabled", havingValue = "true")
public class PortfolioDigestScheduler {
    private static final String DIGEST_LOCK = "PortfolioDigest";

    private final AlertProperties properties;
    private final PortfolioDigestUseCase digestUseCase;
    private final NotificationDispatchUseCase dispatchUseCase;

    @Scheduled(cron = "${ru.invest.api.alerts.digest.scheduler.cron:0 0 10 * * *}",
            zone = "${ru.invest.api.alerts.digest.scheduler.zone:Europe/Moscow}")
    @SchedulerLock(name = DIGEST_LOCK, lockAtLeastFor = "PT30S", lockAtMostFor = "PT30M")
    public void send() {
        if (digestUseCase.sendDue(LocalDate.now(properties.getZone())) > 0) {
            dispatchUseCase.dispatchSoon();
        }
    }
}
