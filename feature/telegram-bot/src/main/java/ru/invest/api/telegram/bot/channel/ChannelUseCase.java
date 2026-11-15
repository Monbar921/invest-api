package ru.invest.api.telegram.bot.channel;

import java.time.Instant;

/**
 * Публичный Telegram-канал: сбор постов в очередь и публикация из неё. При выключенном канале
 * ({@code ru.invest.api.telegram.channel.enabled: false}) все методы ничего не делают.
 */
public interface ChannelUseCase {
    /**
     * Рейтинговые действия и решения по ключевой ставке из ленты за последние max-age - в очередь, без повторов.
     *
     * @return сколько постов поставлено
     */
    int collectNews(Instant now);

    /**
     * Еженедельная подборка самых доходных облигаций по группам рейтинга - одна на неделю.
     *
     * @return true - поставлена; false - уже была на этой неделе, выключена или ни в одной группе нет облигаций
     */
    boolean collectWeeklyTop(Instant now);

    /**
     * Опубликовать следующий пост, если сейчас окно публикации, лимит дня не исчерпан и прошла пауза после прошлого.
     * Устаревшие посты помечаются SKIPPED.
     *
     * @return true - пост опубликован
     */
    boolean publish(Instant now);

    /**
     * Удалить опубликованные, отклонённые и пропущенные посты старше retention.
     */
    int cleanUp(Instant now);
}
