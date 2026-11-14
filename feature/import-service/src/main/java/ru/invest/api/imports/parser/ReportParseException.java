package ru.invest.api.imports.parser;

/**
 * Файл не того формата: сообщение - для пользователя (422).
 */
public class ReportParseException extends RuntimeException {
    public ReportParseException(final String message) {
        super(message);
    }
}
