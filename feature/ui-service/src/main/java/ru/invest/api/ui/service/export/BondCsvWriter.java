package ru.invest.api.ui.service.export;

import ru.invest.api.common.model.BondModel;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CSV для Excel с русской локалью: UTF-8 с BOM (иначе Excel читает кириллицу как Windows-1251), разделитель «;»,
 * десятичная запятая, даты ДД.ММ.ГГГГ.
 */
public final class BondCsvWriter {
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String SEPARATOR = ";";
    private static final String LINE_END = "\r\n";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private BondCsvWriter() {
    }

    public static byte[] write(final List<BondModel> bonds) {
        final List<BondExportTable.Column> columns = BondExportTable.columns();
        final StringBuilder csv = new StringBuilder();
        csv.append(columns.stream().map(column -> escape(column.title())).collect(Collectors.joining(SEPARATOR))).append(LINE_END);
        bonds.forEach(bond -> csv.append(columns.stream()
                        .map(column -> format(column.value().apply(bond)))
                        .collect(Collectors.joining(SEPARATOR)))
                .append(LINE_END));

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(BOM);
        out.writeBytes(csv.toString().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static String format(final Object value) {
        return switch (value) {
            case null -> "";
            case BigDecimal number -> number.stripTrailingZeros().toPlainString().replace('.', ',');
            case LocalDate date -> DATE.format(date);
            default -> escape(value.toString());
        };
    }

    /**
     * Значение с разделителем, кавычкой или переводом строки - в кавычках, кавычки удваиваются.
     */
    private static String escape(final String value) {
        if (value.contains(SEPARATOR) || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
