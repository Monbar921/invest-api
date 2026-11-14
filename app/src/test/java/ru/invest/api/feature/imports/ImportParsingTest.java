package ru.invest.api.feature.imports;

import org.junit.jupiter.api.Test;
import ru.invest.api.imports.config.ImportProperties;
import ru.invest.api.imports.crypto.TokenCipher;
import ru.invest.api.imports.model.ImportKind;
import ru.invest.api.imports.model.TradeCandidate;
import ru.invest.api.imports.parser.ReportParseException;
import ru.invest.api.imports.parser.ReportParser;
import ru.invest.api.imports.parser.ReportValues;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

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

    private static TokenCipher cipher(final String key) {
        final ImportProperties properties = new ImportProperties();
        properties.setTokenEncryptionKey(key);
        return new TokenCipher(properties);
    }

    /** Парсер CSV - пакетный класс: создаётся через конструктор, как это делает Spring */
    private static ReportParser csv() {
        try {
            final Constructor<?> constructor = Class.forName("ru.invest.api.imports.parser.CsvReportParser")
                    .getDeclaredConstructor();
            constructor.setAccessible(true);
            return (ReportParser) constructor.newInstance();
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
