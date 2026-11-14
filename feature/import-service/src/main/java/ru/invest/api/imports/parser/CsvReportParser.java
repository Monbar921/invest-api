package ru.invest.api.imports.parser;

import org.springframework.stereotype.Component;
import ru.invest.api.imports.model.ImportSource;
import ru.invest.api.imports.model.TradeCandidate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Свой шаблон CSV - для любого брокера, чей отчёт можно свести в таблицу. Первая строка - заголовки (по-русски или
 * по-английски, порядок любой), разделитель - «;», «,» или табуляция (по заголовку), кодировка - UTF-8 или
 * Windows-1251. Обязательны: дата, операция (покупка / продажа), ISIN или тикер, количество, цена в % номинала.
 * Необязательны: НКД и комиссия (в валюте номинала, если нет столбца «валюта»), сумма в рублях (валютная облигация
 * куплена за рубли), номер сделки, брокер, название.
 */
@Component
class CsvReportParser implements ReportParser {
    private static final TradeTable.Matchers MATCHERS = new TradeTable.Matchers(
            Set.of("дата", "date", "датасделки", "tradedate")::contains,
            Set.of("операция", "operation", "тип", "type", "вид", "side", "направление")::contains,
            Set.of("isin")::contains,
            Set.of("тикер", "ticker", "secid")::contains,
            Set.of("название", "наименование", "name")::contains,
            Set.of("количество", "колво", "quantity", "qty")::contains,
            Set.of("цена", "price", "ценапроцент", "pricepercent")::contains,
            Set.of("нкд", "aci", "accruedinterest")::contains,
            Set.of("комиссия", "commission", "fee")::contains,
            Set.of("суммавруб", "суммаруб", "amountrub")::contains,
            Set.of("номерсделки", "tradeid", "номер")::contains,
            Set.of("брокер", "broker")::contains,
            Set.of("валюта", "currency")::contains);

    @Override
    public ImportSource format() {
        return ImportSource.CSV;
    }

    @Override
    public ParsedReport parse(final byte[] content) {
        final List<String> lines = ReportValues.text(content).lines().toList();
        int headerLine = 0;
        while (headerLine < lines.size() && lines.get(headerLine).isBlank()) {
            headerLine++;
        }
        if (headerLine == lines.size()) {
            throw new ReportParseException("Файл пустой");
        }
        final char delimiter = delimiter(lines.get(headerLine));
        final TradeTable.Columns columns = TradeTable.Columns.of(split(lines.get(headerLine), delimiter), MATCHERS);
        if (!columns.complete()) {
            throw new ReportParseException("В первой строке CSV нет столбцов: %s. Нужны: дата, операция, ISIN или "
                    .formatted(String.join(", ", columns.missing())) + "тикер, количество, цена в % номинала");
        }
        final TradeTable table = new TradeTable(columns, "csv", null, null, true);
        final List<TradeCandidate> trades = new ArrayList<>();
        for (int i = headerLine + 1; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                // номер строки - как в редакторе, с 1
                trades.add(table.read(i + 1, split(lines.get(i), delimiter)));
            }
        }
        return new ParsedReport(trades, 0);
    }

    /** Разделитель - тот, которого больше в строке заголовков */
    private static char delimiter(final String header) {
        final long semicolons = header.chars().filter(c -> c == ';').count();
        final long tabs = header.chars().filter(c -> c == '\t').count();
        final long commas = header.chars().filter(c -> c == ',').count();
        if (tabs > semicolons && tabs > commas) {
            return '\t';
        }
        return semicolons >= commas && semicolons > 0 ? ';' : ',';
    }

    /** Поля строки CSV: значения в кавычках могут содержать разделитель, «""» - кавычка */
    static List<String> split(final String line, final char delimiter) {
        final List<String> cells = new ArrayList<>();
        final StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delimiter) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }
}
