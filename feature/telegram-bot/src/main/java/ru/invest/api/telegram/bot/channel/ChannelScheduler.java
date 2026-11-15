package ru.invest.api.telegram.bot.channel;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Публикация постов канала (по умолчанию проверка раз в 5 минут), еженедельная подборка (понедельник 11:00 МСК) и
 * очистка старых постов. Шедулер есть при включённом боте; выключенный канал методы use case пропускают сами.
 * Под ShedLock: два экземпляра приложения не опубликуют пост дважды.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.telegram", name = {"enabled", "channel.scheduler.enabled"},
        havingValue = "true")
public class ChannelScheduler {
    private final ChannelUseCase channelUseCase;

    @Scheduled(fixedDelayString = "${ru.invest.api.telegram.channel.scheduler.publish-interval:PT5M}",
            initialDelayString = "PT1M")
    @SchedulerLock(name = "TelegramChannelPublish", lockAtMostFor = "PT5M")
    public void publish() {
        channelUseCase.publish(Instant.now());
    }

    @Scheduled(cron = "${ru.invest.api.telegram.channel.scheduler.weekly-top-cron:0 0 11 * * MON}",
            zone = "${ru.invest.api.telegram.channel.zone:Europe/Moscow}")
    @SchedulerLock(name = "TelegramChannelWeeklyTop", lockAtLeastFor = "PT30S", lockAtMostFor = "PT10M")
    public void weeklyTop() {
        channelUseCase.collectWeeklyTop(Instant.now());
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "${ru.invest.api.telegram.channel.zone:Europe/Moscow}")
    @SchedulerLock(name = "TelegramChannelCleanUp", lockAtMostFor = "PT10M")
    public void cleanUp() {
        channelUseCase.cleanUp(Instant.now());
    }
}
