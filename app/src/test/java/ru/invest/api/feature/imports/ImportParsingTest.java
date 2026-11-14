package ru.invest.api.feature.imports;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ru.invest.api.imports.config.ImportProperties;
import ru.invest.api.imports.crypto.TokenCipher;
import ru.invest.api.imports.model.ImportKind;
import ru.invest.api.imports.model.TradeCandidate;
import ru.invest.api.imports.parser.ReportParseException;
import ru.invest.api.imports.parser.ReportParser;
import ru.invest.api.imports.parser.ReportValues;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Без Spring и БД: шифрование токена, разбор CSV (разделители, кавычки, кодировка, ключи строк) и значений отчётов.
 */
class ImportParsingTest {
    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String TOKEN = "t.SecretReadOnlyToken-0123456789";

    @Test
    void tokenCipherRoundTripAndBindsUser() {
        final TokenCipher cipher = cipher(KEY);
        final String first = cipher.encrypt(TOKEN, "alice");
        final String second = cipher.encrypt(TOKEN, "alice");

        assertThat(first).startsWith("v1:").doesNotContain(TOKEN);
        // случайный IV: один и тот же токен - разные шифртексты
        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first, "alice")).isEqualTo(TOKEN);
        // чужой пользователь, другой ключ, испорченные данные - не расшифровать
        assertThatThrownBy(() -> cipher.decrypt(first, "bob")).isInstanceOf(TokenCipher.TokenDecryptionException.class);
        assertThatThrownBy(() -> cipher("ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=").decrypt(first, "alice"))
                .isInstanceOf(TokenCipher.TokenDecryptionException.class);
        assertThatThrownBy(() -> cipher.decrypt("v1:AAAA", "alice"))
                .isInstanceOf(TokenCipher.TokenDecryptionException.class)
                .hasMessageNotContaining(TOKEN);
        assertThat(cipher("").isConfigured()).isFalse();
    }

    @Test
    void encryptionKeyMustBe32Bytes() {
        final ImportProperties properties = new ImportProperties();
        properties.setTokenEncryptionKey("c2hvcnQ=");
        assertThat(properties.isTokenEncryptionKeyValid()).isFalse();
        properties.setTokenEncryptionKey("not base64!");
        assertThat(properties.isTokenEncryptionKeyValid()).isFalse();
        properties.setTokenEncryptionKey(KEY);
        assertThat(properties.isTokenEncryptionKeyValid()).isTrue();
        properties.setTokenEncryptionKey(null);
        assertThat(properties.isTokenEncryptionKeyValid()).isTrue();
        assertThat(properties.isTokenImportEnabled()).isFalse();
    }

    @Test
    void csvWithCommasQuotesAndEnglishHeaders() {
        final String csv = """
                date,operation,ticker,quantity,price,commission,trade_id
                2026-10-01,BUY,SU26238RMFS4,10,"60,5",1.5,A-1
                2026-10-02,sell,SU26238RMFS4,2,61,"0,3",A-2
                """;
        final List<TradeCandidate> trades = csv().parse(csv.getBytes(StandardCharsets.UTF_8)).trades();

        assertThat(trades).hasSize(2);
        assertThat(trades.getFirst().kind()).isEqualTo(ImportKind.BUY);
        assertThat(trades.getFirst().ticker()).isEqualTo("SU26238RMFS4");
        assertThat(trades.getFirst().pricePercent()).isEqualByComparingTo("60.5");
        assertThat(trades.getFirst().commission().amount()).isEqualByComparingTo("1.5");
        assertThat(trades.getFirst().lineNo()).isEqualTo(2);
        // номер сделки - ключ
        assertThat(trades.getFirst().importKey()).isEqualTo("csv::A-1");
        assertThat(trades.get(1).kind()).isEqualTo(ImportKind.SELL);
        assertThat(trades.get(1).commission().amount()).isEqualByComparingTo("0.3");
    }

    @Test
    void csvInWindows1251AndIdenticalRowsGetDifferentKeys() {
        final String csv = "Дата;Операция;ISIN;Количество;Цена\r\n"
                + "01.10.2026;Покупка;RU000A1038V6;1;60\r\n"
                + "01.10.2026;Покупка;RU000A1038V6;1;60\r\n"
                + "01.10.2026;Покупка;RU000A1038V6;0;60\r\n"
                + "32.10.2026;Покупка;RU000A1038V6;1;60\r\n";
        final byte[] content = csv.getBytes(Charset.forName("windows-1251"));
        final List<TradeCandidate> trades = csv().parse(content).trades();

        assertThat(trades).hasSize(4);
        assertThat(trades.getFirst().isin()).isEqualTo("RU000A1038V6");
        assertThat(trades.getFirst().date()).isEqualTo(LocalDate.of(2026, 10, 1));
        // две одинаковые сделки за день - две разные, и при повторном разборе ключи те же
        assertThat(trades.get(0).importKey()).isNotEqualTo(trades.get(1).importKey());
        assertThat(csv().parse(content).trades().stream().map(TradeCandidate::importKey).toList())
                .isEqualTo(trades.stream().map(TradeCandidate::importKey).toList());
        assertThat(trades.get(2).error()).contains("Количество");
        assertThat(trades.get(3).error()).contains("Дата");
    }

    @Test
    void csvWithoutRequiredColumnsIsRejected() {
        assertThatThrownBy(() -> csv().parse("Дата;Количество\n01.10.2026;1\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ReportParseException.class)
                .hasMessageContaining("операция")
                .hasMessageContaining("цена");
        assertThatThrownBy(() -> csv().parse(new byte[]{'\n'})).isInstanceOf(ReportParseException.class);
    }

    @Test
    void reportValues() {
        assertThat(ReportValues.number("1 234,56")).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(ReportValues.number(" -0,5")).isEqualByComparingTo(new BigDecimal("-0.5"));
        assertThat(ReportValues.number("—")).isNull();
        assertThat(ReportValues.date("14.11.2026 10:15:00")).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(ReportValues.date("2026-11-14")).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(ReportValues.date("Итого")).isNull();
        assertThat(ReportValues.kind("Купля")).isEqualTo(ImportKind.BUY);
        assertThat(ReportValues.kind("РЕПО 1 часть")).isNull();
        assertThat(ReportValues.isIsin("RU000A1038V6")).isTrue();
        assertThat(ReportValues.isIsin("SU26238RMFS4")).isTrue();
        assertThat(ReportValues.isIsin("ОФЗ 26238")).isFalse();
        assertThat(ReportValues.header("Цена, %")).isEqualTo("цена");
    }

    /**
     * Отчёт Альфа-Банка: таблица по заголовкам на листах сделок, направление по знаку количества, комиссия в валюте из
     * своего столбца, номер сделки (два числа через перевод строки) - ключ; сноски и другие листы пропускаются.
     */
    @Test
    void alfaXlsxReport() throws IOException {
        final String header = row(15, "E", "№ сделки", "H", "Дата\nзаключен.", "K", "Дата\nрасчетов", "M", "ISIN/рег.код",
                "O", "Актив", "Q", "Количество актива⁷,\nшт./грамм", "S", "Цена", "T", "Сумма сделки \nв валюте расчетов⁸",
                "V", "В т.ч. НКД в валюте расчетов", "X", "Валюта расчетов", "Y", "\nКомиссия\nбанка", "AA",
                "Валюта комиссии");
        final String buy = row(16, "E", "1095618553\n1217051208", "H", "15.09.2025\n 17:44:14", "K", "16.09.2025",
                "M", "RU000A10C1S1", "O", "Газпром нефть 006Р-01", "Q", "20.0000000000", "S", "99.98000000", "T",
                "198899.19", "V", "278.13", "X", "RUB", "Y", "1.40", "AA", "RUB");
        final String sell = row(17, "E", "1290880225\n1375466319", "H", "22.12.2025\n 18:47:55", "M", "RU000A10C1S1",
                "O", "Газпром нефть 006Р-01", "Q", "-1.0000000000", "S", "98.44000000", "V", "29.30", "X", "RUB", "Y",
                "0.07", "AA", "RUB");
        final String footnote = row(18, "L", "Количество актива со знаком минус - выбытие актива");
        final byte[] xlsx = xlsx(List.of("Динамика позиций", "Завершенные сделки"),
                List.of(row(15, "E", "Дата", "H", "Цена"), header + buy + sell + footnote));

        final List<TradeCandidate> trades = parser("AlfaXlsxReportParser").parse(xlsx).trades();

        assertThat(trades).hasSize(2);
        final TradeCandidate first = trades.getFirst();
        assertThat(first.kind()).isEqualTo(ImportKind.BUY);
        assertThat(first.isin()).isEqualTo("RU000A10C1S1");
        assertThat(first.date()).isEqualTo(LocalDate.of(2025, 9, 15));
        assertThat(first.quantity()).isEqualTo(20);
        assertThat(first.pricePercent()).isEqualByComparingTo("99.98");
        assertThat(first.aci().amount()).isEqualByComparingTo("278.13");
        assertThat(first.commission().amount()).isEqualByComparingTo("1.40");
        assertThat(first.commission().currency()).isEqualTo("rub");
        assertThat(first.broker()).isEqualTo("Альфа-Банк");
        // сумма сделки - с НКД: рубли за облигации - без него
        assertThat(first.amountRub()).isEqualByComparingTo("198621.06");
        assertThat(first.importKey()).isEqualTo("alfa:альфабанк:1095618553 1217051208");
        assertThat(trades.get(1).kind()).isEqualTo(ImportKind.SELL);
        assertThat(trades.get(1).quantity()).isEqualTo(1);
        assertThat(trades.get(1).amountRub()).isNull();
        // тот же номер сделки - тот же ключ: сделка из «Незавершенных» потом окажется в «Завершенных»
        assertThat(parser("AlfaXlsxReportParser").parse(xlsx).trades().getFirst().importKey())
                .isEqualTo(first.importKey());
        assertThatThrownBy(() -> parser("AlfaXlsxReportParser").parse("not a zip".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ReportParseException.class);
        assertThatThrownBy(() -> parser("AlfaXlsxReportParser").parse(xlsx(List.of("Лист1"), List.of(header))))
                .isInstanceOf(ReportParseException.class);
    }

    /** Настоящий отчёт - только вручную: -Dalfa.report=путь к .xlsx (в репозиторий не кладётся - персональные данные) */
    @Test
    @EnabledIfSystemProperty(named = "alfa.report", matches = ".+")
    void realAlfaReport() throws IOException {
        final ReportParser.ParsedReport report = parser("AlfaXlsxReportParser")
                .parse(Files.readAllBytes(Path.of(System.getProperty("alfa.report"))));
        report.trades().forEach(trade -> System.out.printf("ALFA %d %s %s %s %s x%s @%s aci=%s fee=%s rub=%s key=%s err=%s%n",
                trade.lineNo(), trade.date(), trade.kind(), trade.isin(), trade.name(), trade.quantity(),
                trade.pricePercent(), trade.aci() == null ? null : trade.aci().amount(),
                trade.commission() == null ? null : trade.commission().amount(), trade.amountRub(), trade.importKey(),
                trade.error()));
        assertThat(report.trades()).isNotEmpty();
        assertThat(report.trades()).allSatisfy(trade -> assertThat(trade.error()).isNull());
    }

    private static TokenCipher cipher(final String key) {
        final ImportProperties properties = new ImportProperties();
        properties.setTokenEncryptionKey(key);
        return new TokenCipher(properties);
    }

    private static ReportParser csv() {
        return parser("CsvReportParser");
    }

    /** Парсеры - пакетные классы: создаются через конструктор, как это делает Spring */
    private static ReportParser parser(final String name) {
        try {
            final Constructor<?> constructor = Class.forName("ru.invest.api.imports.parser." + name)
                    .getDeclaredConstructor();
            constructor.setAccessible(true);
            return (ReportParser) constructor.newInstance();
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Строка листа XLSX со встроенными строками: пары «столбец, значение» */
    private static String row(final int number, final String... cells) {
        final StringBuilder xml = new StringBuilder("<row r=\"%d\">".formatted(number));
        for (int i = 0; i < cells.length; i += 2) {
            xml.append("<c r=\"%s%d\" t=\"inlineStr\"><is><t>%s</t></is></c>".formatted(cells[i], number,
                    cells[i + 1].replace("&", "&amp;").replace("<", "&lt;")));
        }
        return xml.append("</row>").toString();
    }

    /** Минимальный XLSX: книга, связи и листы */
    private static byte[] xlsx(final List<String> names, final List<String> sheets) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            final StringBuilder workbook = new StringBuilder("<workbook xmlns=\"http://schemas.openxmlformats.org/"
                    + "spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/"
                    + "relationships\"><sheets>");
            final StringBuilder rels = new StringBuilder("<Relationships xmlns=\"http://schemas.openxmlformats.org/"
                    + "package/2006/relationships\">");
            for (int i = 0; i < names.size(); i++) {
                workbook.append("<sheet name=\"%s\" sheetId=\"%d\" r:id=\"rId%d\"/>".formatted(names.get(i), i + 1,
                        i + 1));
                rels.append("<Relationship Id=\"rId%d\" Target=\"/xl/worksheets/sheet%d.xml\"/>".formatted(i + 1,
                        i + 1));
                put(zip, "xl/worksheets/sheet%d.xml".formatted(i + 1), "<worksheet xmlns=\"http://schemas."
                        + "openxmlformats.org/spreadsheetml/2006/main\"><sheetData>" + sheets.get(i)
                        + "</sheetData></worksheet>");
            }
            put(zip, "xl/workbook.xml", workbook.append("</sheets></workbook>").toString());
            put(zip, "xl/_rels/workbook.xml.rels", rels.append("</Relationships>").toString());
        }
        return out.toByteArray();
    }

    private static void put(final ZipOutputStream zip, final String name, final String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
