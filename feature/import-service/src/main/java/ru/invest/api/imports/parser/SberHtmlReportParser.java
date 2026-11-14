package ru.invest.api.imports.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import ru.invest.api.imports.model.ImportSource;
import ru.invest.api.imports.model.TradeCandidate;

import java.util.ArrayList;
import java.util.List;

/**
 * Брокерский отчёт Сбербанка в HTML. Сделки - в таблице «Сделки купли/продажи ценных бумаг»: её ищем не по
 * названию, а по заголовкам столбцов (дата заключения, вид, ISIN или код бумаги, количество, цена), поэтому порядок и
 * лишние столбцы не важны. Цена облигации в отчёте - в % номинала, комиссии (банка и биржи) складываются и считаются
 * в рублях, НКД - в валюте из столбца «Валюта». Строки итогов и подзаголовков пропускаются.
 * <p>
 * Названия столбцов взяты из описания отчёта; на реальном отчёте разбор нужно проверить (docs/features, «Что
 * осталось»).
 */
@Component
class SberHtmlReportParser implements ReportParser {
    static final String BROKER = "Сбербанк";
    private static final TradeTable.Matchers MATCHERS = new TradeTable.Matchers(
            header -> header.startsWith("датазаключ") || "датасделки".equals(header),
            header -> "вид".equals(header) || header.startsWith("видсделки") || header.contains("покупкапродажа")
                    || header.startsWith("направление") || header.startsWith("операция"),
            header -> header.contains("isin") || header.startsWith("кодцб") || header.startsWith("кодценнойбумаги"),
            header -> false,
            header -> header.startsWith("наименование"),
            header -> header.startsWith("количество") || header.startsWith("колво"),
            header -> header.startsWith("цена"),
            header -> header.startsWith("нкд"),
            header -> header.contains("комисси"),
            header -> false,
            header -> header.startsWith("номерсделки"),
            header -> false,
            header -> header.startsWith("валюта"));

    @Override
    public ImportSource format() {
        return ImportSource.SBER_HTML;
    }

    @Override
    public ParsedReport parse(final byte[] content) {
        final Document document = Jsoup.parse(ReportValues.text(content));
        final List<TradeCandidate> trades = new ArrayList<>();
        int skipped = 0;
        boolean found = false;
        int lineNo = 0;
        for (final Element table : document.select("table")) {
            TradeTable trade = null;
            for (final Element row : table.select("> tbody > tr, > thead > tr, > tr")) {
                lineNo++;
                final List<String> cells = row.select("> td, > th").stream().map(Element::text).toList();
                final TradeTable.Columns columns = TradeTable.Columns.of(cells, MATCHERS);
                if (columns.complete()) {
                    // заголовок таблицы сделок (их может быть несколько - по площадкам)
                    trade = new TradeTable(columns, "sber", BROKER, "rub", false);
                    found = true;
                    continue;
                }
                if (trade != null) {
                    final TradeCandidate candidate = trade.read(lineNo, cells);
                    if (candidate != null) {
                        trades.add(candidate);
                    } else if (cells.size() > 1 && ReportValues.date(cells.getFirst()) != null) {
                        // строка с датой, но не покупка и не продажа (например, РЕПО)
                        skipped++;
                    }
                }
            }
        }
        if (!found) {
            throw new ReportParseException("В файле не найдена таблица сделок купли/продажи ценных бумаг. Нужен "
                    + "брокерский отчёт Сбербанка в формате HTML");
        }
        return new ParsedReport(trades, skipped);
    }
}
