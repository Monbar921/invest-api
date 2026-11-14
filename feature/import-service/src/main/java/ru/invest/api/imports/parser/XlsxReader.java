package ru.invest.api.imports.parser;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Значения ячеек листов XLSX без библиотеки разбора Excel: XLSX - zip с XML листов. Читаются строки (общие и
 * встроенные), числа и даты как текст - отчётам брокеров этого хватает. Защита: внешние сущности XML запрещены,
 * распакованный файл не больше {@link #MAX_UNPACKED} байт, частей - не больше {@link #MAX_ENTRIES}.
 */
final class XlsxReader {
    static final long MAX_UNPACKED = 100L * 1024 * 1024;
    static final int MAX_ENTRIES = 1000;
    private static final String MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String RELATIONSHIPS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final int LETTERS = 26;
    private static final int BUFFER = 8192;

    private XlsxReader() {
    }

    /**
     * Листы книги по порядку: имя → строки; строка - номер строки в листе (с 1) и ячейки по номерам столбцов (A - 0),
     * пропущенные ячейки - пустые строки.
     *
     * @throws ReportParseException не XLSX
     */
    static Map<String, List<Row>> read(final byte[] content) {
        final Map<String, byte[]> parts = unzip(content);
        if (!parts.containsKey("xl/workbook.xml")) {
            throw new ReportParseException("Файл не в формате Excel (.xlsx)");
        }
        try {
            final List<String> shared = parts.containsKey("xl/sharedStrings.xml")
                    ? sharedStrings(parts.get("xl/sharedStrings.xml"))
                    : List.of();
            final Map<String, String> targets = relationships(parts.get("xl/_rels/workbook.xml.rels"));
            final Map<String, List<Row>> sheets = new LinkedHashMap<>();
            for (final Map.Entry<String, String> sheet : sheets(parts.get("xl/workbook.xml")).entrySet()) {
                final String target = targets.get(sheet.getValue());
                if (target == null) {
                    continue;
                }
                final String path = target.startsWith("/") ? target.substring(1) : "xl/" + target;
                if (parts.containsKey(path)) {
                    sheets.put(sheet.getKey(), rows(parts.get(path), shared));
                }
            }
            return sheets;
        } catch (final XMLStreamException e) {
            throw new ReportParseException("Файл Excel повреждён: " + e.getMessage());
        }
    }

    private static Map<String, byte[]> unzip(final byte[] content) {
        final Map<String, byte[]> parts = new HashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (parts.size() >= MAX_ENTRIES) {
                    throw new ReportParseException("Файл Excel слишком сложный");
                }
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                final byte[] buffer = new byte[BUFFER];
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_UNPACKED) {
                        throw new ReportParseException("Файл Excel слишком большой");
                    }
                    out.write(buffer, 0, read);
                }
                parts.put(entry.getName(), out.toByteArray());
            }
        } catch (final IOException | IllegalArgumentException e) {
            throw new ReportParseException("Файл не в формате Excel (.xlsx)");
        }
        return parts;
    }

    private static XMLStreamReader reader(final byte[] xml) throws XMLStreamException {
        final XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        return factory.createXMLStreamReader(new ByteArrayInputStream(xml == null ? new byte[0] : xml));
    }

    /** Имя листа → id связи */
    private static Map<String, String> sheets(final byte[] workbook) throws XMLStreamException {
        final Map<String, String> sheets = new LinkedHashMap<>();
        final XMLStreamReader xml = reader(workbook);
        while (xml.hasNext()) {
            if (xml.next() == XMLStreamConstants.START_ELEMENT && "sheet".equals(xml.getLocalName())) {
                sheets.put(xml.getAttributeValue(null, "name"), xml.getAttributeValue(RELATIONSHIPS, "id"));
            }
        }
        return sheets;
    }

    /** id связи → путь части */
    private static Map<String, String> relationships(final byte[] rels) throws XMLStreamException {
        final Map<String, String> targets = new HashMap<>();
        if (rels == null) {
            return targets;
        }
        final XMLStreamReader xml = reader(rels);
        while (xml.hasNext()) {
            if (xml.next() == XMLStreamConstants.START_ELEMENT && "Relationship".equals(xml.getLocalName())) {
                targets.put(xml.getAttributeValue(null, "Id"), xml.getAttributeValue(null, "Target"));
            }
        }
        return targets;
    }

    private static List<String> sharedStrings(final byte[] part) throws XMLStreamException {
        final List<String> strings = new ArrayList<>();
        final XMLStreamReader xml = reader(part);
        StringBuilder current = null;
        while (xml.hasNext()) {
            final int event = xml.next();
            if (event == XMLStreamConstants.START_ELEMENT && "si".equals(xml.getLocalName())) {
                current = new StringBuilder();
            } else if (event == XMLStreamConstants.START_ELEMENT && "t".equals(xml.getLocalName()) && current != null) {
                current.append(xml.getElementText());
            } else if (event == XMLStreamConstants.END_ELEMENT && "si".equals(xml.getLocalName()) && current != null) {
                strings.add(current.toString());
                current = null;
            }
        }
        return strings;
    }

    private static List<Row> rows(final byte[] sheet, final List<String> shared) throws XMLStreamException {
        final List<Row> rows = new ArrayList<>();
        final XMLStreamReader xml = reader(sheet);
        List<String> cells = null;
        int rowNumber = 0;
        int column = -1;
        String type = null;
        StringBuilder value = null;
        while (xml.hasNext()) {
            final int event = xml.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (xml.getLocalName()) {
                    case "row" -> {
                        cells = new ArrayList<>();
                        final String number = xml.getAttributeValue(null, "r");
                        rowNumber = number == null ? rowNumber + 1 : Integer.parseInt(number);
                    }
                    case "c" -> {
                        final String ref = xml.getAttributeValue(null, "r");
                        column = ref == null ? (cells == null ? 0 : cells.size()) : column(ref);
                        type = xml.getAttributeValue(null, "t");
                        value = new StringBuilder();
                    }
                    case "v", "t" -> {
                        if (value != null && MAIN.equals(xml.getNamespaceURI())) {
                            value.append(xml.getElementText());
                        }
                    }
                    default -> {
                        // остальное не нужно
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("c".equals(xml.getLocalName()) && cells != null && value != null) {
                    while (cells.size() < column) {
                        cells.add("");
                    }
                    cells.add(cellText(type, value.toString(), shared));
                    value = null;
                } else if ("row".equals(xml.getLocalName()) && cells != null) {
                    rows.add(new Row(rowNumber, cells));
                    cells = null;
                }
            }
        }
        return rows;
    }

    private static String cellText(final String type, final String raw, final List<String> shared) {
        if ("s".equals(type)) {
            try {
                final int index = Integer.parseInt(raw.strip());
                return index >= 0 && index < shared.size() ? shared.get(index) : "";
            } catch (final NumberFormatException e) {
                return "";
            }
        }
        return raw;
    }

    /** «AB12» → 27 */
    private static int column(final String ref) {
        int column = 0;
        for (int i = 0; i < ref.length() && Character.isLetter(ref.charAt(i)); i++) {
            column = column * LETTERS + (Character.toUpperCase(ref.charAt(i)) - 'A' + 1);
        }
        return column - 1;
    }

    /** Строка листа */
    record Row(int number, List<String> cells) {
    }
}
