package ru.invest.api.common.model.news;

import java.util.regex.Pattern;

/**
 * Решение Банка России по ключевой ставке - по заголовку пресс-релиза. Общее для правила уведомлений «ключевая ставка»
 * и постов публичного Telegram-канала.
 */
public final class KeyRateDecision {
    // «Банк России принял решение сохранить ключевую ставку на уровне 14,00% годовых» (так ЦБ пишет сейчас),
    // «Банк России снизил ключевую ставку на 50 б.п.» - и неопределённая форма, и прошедшее время
    private static final Pattern DECISION = Pattern.compile(
            "(снизи|повыси|сохрани)(л|ть)\\s+ключевую\\s+ставку", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private KeyRateDecision() {
    }

    /**
     * @return true - заголовок пресс-релиза ЦБ о решении по ключевой ставке
     */
    public static boolean matches(final String title) {
        return title != null && DECISION.matcher(title).find();
    }
}
