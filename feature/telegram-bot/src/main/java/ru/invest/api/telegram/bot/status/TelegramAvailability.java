package ru.invest.api.telegram.bot.status;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Последний известный результат обращения к Telegram: проверка getMe и отправка сообщений.
 * Только в памяти - после перезапуска неизвестно, пока не пройдёт первая проверка.
 */
@Component
public class TelegramAvailability {
    private final AtomicReference<State> state = new AtomicReference<>();

    public void success() {
        state.set(new State(true, LocalDateTime.now(ZoneOffset.UTC)));
    }

    public void failure() {
        state.set(new State(false, LocalDateTime.now(ZoneOffset.UTC)));
    }

    /**
     * @return null - к Telegram ещё не обращались
     */
    public State get() {
        return state.get();
    }

    public record State(boolean available, LocalDateTime checkedAt) {
    }
}
