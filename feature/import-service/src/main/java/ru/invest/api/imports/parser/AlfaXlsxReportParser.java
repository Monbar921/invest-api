package ru.invest.api.imports.parser;

import org.springframework.stereotype.Component;
import ru.invest.api.imports.model.ImportSource;
import ru.invest.api.imports.model.TradeCandidate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Брокерский отчёт Альфа-Банка (Альфа-Инвестиции) в XLSX. Сделки - на листах «Завершенные сделки» и «Незавершенные
 * сделки» (исполненные, но ещё не рассчитанные): таблица находится по заголовкам (№ сделки, дата заключения,
 * ISIN/рег.код, количество, цена). Столбца «покупка / продажа» нет - направление по знаку количества («-» - выбытие,
 * то есть продажа). Цена облигации - в % номинала, НКД - в валюте расчётов, комиссия - в валюте из «Валюта комиссии».
 * Номер сделки (два числа через перевод строки) - ключ импорта. Сумма сделки - в валюте расчётов и с НКД: при расчётах
 * в рублях из неё - рубли за облигации (замещающие облигации). Акции и фонды попадают в «нет в системе».
 */
@Component
class AlfaXlsxReportParser implements ReportParser {
    static final String BROKER = "Альфа-Банк";
    private static final TradeTable.Matchers MATCHERS = new TradeTable.Matchers(
            header -> header.startsWith("датазаключ"),
            header -> false,
            header -> header.startsWith("isin"),
            header -> false,
            header -> "актив".equals(header),
            header -> header.startsWith("количество"),
            header -> "цена".equals(header),
            header -> header.contains("нкд"),
            header -> header.startsWith("комисси"),
            header -> header.startsWith("суммасделки"),
            header -> header.startsWith("сделки") || header.startsWith("номерсделки"),
            header -> false,
            header -> header.startsWith("валютарасч"),
            header -> header.startsWith("валютакомисси"));

    @Override
    public ImportSource format() {
        return ImportSource.ALFA_XLSX;
    }

    @Override
    public ParsedReport parse(final byte[] content) {
        final Map<String, List<XlsxReader.Row>> sheets = XlsxReader.read(content);
        final List<TradeCandidate> trades = new ArrayList<>();
        boolean found = false;
        // номер строки в импорте - сквозной по листам: лист × 100 000 + строка листа
        int sheetIndex = 0;
        for (final Map.Entry<String, List<XlsxReader.Row>> sheet : sheets.entrySet()) {
            sheetIndex++;
            if (!sheet.getKey().toLowerCase().contains("сделки")) {
                continue;
            }
            TradeTable table = null;
            for (final XlsxReader.Row row : sheet.getValue()) {
                final TradeTable.Columns columns = TradeTable.Columns.of(row.cells(), MATCHERS);
                if (columns.completeWithSignedQuantity()) {
                    table = new TradeTable(columns, "alfa", BROKER, null, true, false);
                    found = true;
                    continue;
                }
                if (table != null) {
                    final TradeCandidate candidate = table.read(sheetIndex * 100_000 + row.number(), row.cells());
                    if (candidate != null) {
                        trades.add(candidate);
                    }
                }
            }
        }
        if (!found) {
            throw new ReportParseException("В файле не найдены листы «Завершенные сделки» с таблицей сделок. Нужен "
                    + "брокерский отчёт Альфа-Инвестиций в формате Excel (.xlsx)");
        }
        return new ParsedReport(trades, 0);
    }
}
