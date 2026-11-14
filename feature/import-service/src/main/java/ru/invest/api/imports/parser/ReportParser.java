package ru.invest.api.imports.parser;

import ru.invest.api.imports.model.ImportSource;
import ru.invest.api.imports.model.TradeCandidate;

import java.util.List;

/**
 * Разбор файла отчёта одного брокера (формата) в сделки. Новый брокер - новая реализация-бин; формат выбирает
 * пользователь. Строка, которую не разобрать, - {@link TradeCandidate#invalid}, а не исключение: её покажет отчёт.
 */
public interface ReportParser {
    ImportSource format();

    /**
     * @throws ReportParseException файл целиком не этого формата (нет таблицы сделок, нет нужных столбцов)
     */
    ParsedReport parse(byte[] content);

    /**
     * @param trades  покупки и продажи (облигации и, если отчёт их не различает, другие бумаги)
     * @param skipped строк таблицы сделок, которые не покупка и не продажа (например, РЕПО)
     */
    record ParsedReport(List<TradeCandidate> trades, int skipped) {
    }
}
