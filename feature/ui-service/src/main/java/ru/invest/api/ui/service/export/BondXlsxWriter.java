package ru.invest.api.ui.service.export;

import ru.invest.api.common.model.BondModel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Минимальный файл Excel (Office Open XML): один лист, строка заголовков закреплена и с автофильтром, числа - числами,
 * даты - датами Excel. Отдельная библиотека не нужна: XLSX - zip с несколькими XML-файлами.
 */
public final class BondXlsxWriter {
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);
    private static final int STYLE_HEADER = 1;
    private static final int STYLE_DATE = 2;
    private static final int LETTERS = 26;
    private static final int COLUMN_WIDTH = 18;

    private static final String CONTENT_TYPES = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
            <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
            <Default Extension="xml" ContentType="application/xml"/>
            <Override PartName="/xl/workbook.xml" \
            ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
            <Override PartName="/xl/worksheets/sheet1.xml" \
            ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            <Override PartName="/xl/styles.xml" \
            ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
            </Types>""";
    private static final String ROOT_RELS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" \
            Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>""";
    private static final String WORKBOOK = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" \
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
            <sheets><sheet name="Облигации" sheetId="1" r:id="rId1"/></sheets>
            <definedNames><definedName name="_xlnm._FilterDatabase" localSheetId="0" hidden="1">\
            'Облигации'!$A$1:$%s$%d</definedName></definedNames>
            </workbook>""";
    private static final String WORKBOOK_RELS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" \
            Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
            <Relationship Id="rId2" \
            Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            </Relationships>""";
    // стили ячеек: 0 - обычный, 1 - заголовок (жирный), 2 - дата (встроенный формат 14 - дата по локали Excel)
    private static final String STYLES = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
            <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
            <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
            <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
            <cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>\
            <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>\
            <xf numFmtId="14" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>
            </styleSheet>""";

    private BondXlsxWriter() {
    }

    public static byte[] write(final List<BondModel> bonds) {
        final List<BondExportTable.Column> columns = BondExportTable.columns();
        final String lastColumn = columnName(columns.size() - 1);
        final int lastRow = bonds.size() + 1;

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            entry(zip, "[Content_Types].xml", CONTENT_TYPES);
            entry(zip, "_rels/.rels", ROOT_RELS);
            entry(zip, "xl/workbook.xml", WORKBOOK.formatted(lastColumn, lastRow));
            entry(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
            entry(zip, "xl/styles.xml", STYLES);
            entry(zip, "xl/worksheets/sheet1.xml", sheet(columns, bonds, lastColumn, lastRow));
        } catch (final IOException e) {
            throw new UncheckedIOException("XLSX is not written", e);
        }
        return out.toByteArray();
    }

    private static String sheet(final List<BondExportTable.Column> columns, final List<BondModel> bonds,
                                final String lastColumn, final int lastRow) {
        final StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetViews><sheetView workbookViewId="0">\
                <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>
                """);
        xml.append("<cols><col min=\"1\" max=\"").append(columns.size()).append("\" width=\"").append(COLUMN_WIDTH)
                .append("\" customWidth=\"1\"/></cols><sheetData>");

        xml.append("<row r=\"1\">");
        for (int i = 0; i < columns.size(); i++) {
            textCell(xml, cellRef(i, 1), columns.get(i).title(), STYLE_HEADER);
        }
        xml.append("</row>");

        int rowNumber = 2;
        for (final BondModel bond : bonds) {
            xml.append("<row r=\"").append(rowNumber).append("\">");
            for (int i = 0; i < columns.size(); i++) {
                cell(xml, cellRef(i, rowNumber), columns.get(i).value().apply(bond));
            }
            xml.append("</row>");
            rowNumber++;
        }
        xml.append("</sheetData><autoFilter ref=\"A1:").append(lastColumn).append(lastRow).append("\"/></worksheet>");
        return xml.toString();
    }

    private static void cell(final StringBuilder xml, final String ref, final Object value) {
        switch (value) {
            case null -> {
                // пустая ячейка - значения нет
            }
            case BigDecimal number -> xml.append("<c r=\"").append(ref).append("\"><v>")
                    .append(number.stripTrailingZeros().toPlainString()).append("</v></c>");
            case LocalDate date -> xml.append("<c r=\"").append(ref).append("\" s=\"").append(STYLE_DATE).append("\"><v>")
                    .append(ChronoUnit.DAYS.between(EXCEL_EPOCH, date)).append("</v></c>");
            default -> textCell(xml, ref, value.toString(), 0);
        }
    }

    private static void textCell(final StringBuilder xml, final String ref, final String text, final int style) {
        xml.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"");
        if (style != 0) {
            xml.append(" s=\"").append(style).append('"');
        }
        xml.append("><is><t xml:space=\"preserve\">").append(escape(text)).append("</t></is></c>");
    }

    private static String cellRef(final int column, final int row) {
        return columnName(column) + row;
    }

    /**
     * Буквы столбца: 0 - A, 25 - Z, 26 - AA.
     */
    static String columnName(final int index) {
        final StringBuilder name = new StringBuilder();
        int number = index + 1;
        while (number > 0) {
            final int remainder = (number - 1) % LETTERS;
            name.insert(0, (char) ('A' + remainder));
            number = (number - 1) / LETTERS;
        }
        return name.toString();
    }

    /**
     * Экранирование XML; управляющие символы (кроме табуляции и переводов строки) в XML 1.0 недопустимы - убираются.
     */
    private static String escape(final String text) {
        final StringBuilder escaped = new StringBuilder(text.length());
        text.codePoints().forEach(point -> {
            switch (point) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                default -> {
                    if (point >= ' ' || point == '\t' || point == '\n' || point == '\r') {
                        escaped.appendCodePoint(point);
                    }
                }
            }
        });
        return escaped.toString();
    }

    private static void entry(final ZipOutputStream zip, final String name, final String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
