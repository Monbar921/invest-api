package ru.invest.api.imports.parser;

import ru.invest.api.imports.model.ImportKind;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Значения из отчётов брокеров: текст в UTF-8 или Windows-1251, числа с запятой и пробелами, даты по-русски и ISO,
 * «покупка / продажа», ключ импорта строки.
 */
public final class ReportValues {
    private static final Pattern ISIN = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00a0\\u202f]+");
    private static final List<DateTimeFormatter> DATES = List.of(DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd.MM.yyyy"), DateTimeFormatter.ofPattern("dd.MM.yy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    private static final int DATE_LENGTH = 10;
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");

    private ReportValues() {
    }

    /** Текст файла: UTF-8 (BOM отбрасывается), а если это не UTF-8 - Windows-1251 (выгрузки Excel по-русски) */
    public static String text(final byte[] content) {
        try {
            final String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (final CharacterCodingException e) {
            return new String(content, WINDOWS_1251);
        }
    }

    /** Заголовок столбца для сравнения: нижний регистр, ё → е, без знаков и пробелов */
    public static String header(final String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** Число «1 234,56», «1234.56», «-0,5»; пусто или «-» - null */
    public static BigDecimal number(final String value) {
        if (value == null) {
            return null;
        }
        final String cleaned = SPACES.matcher(value).replaceAll("").replace(',', '.').replace("%", "");
        if (cleaned.isEmpty() || "-".equals(cleaned) || "—".equals(cleaned)) {
            return null;
        }
        return new BigDecimal(cleaned);
    }

    /** Дата «2026-11-14», «14.11.2026», «14.11.2026 10:15:00»; не разобрать - null */
    public static LocalDate date(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String trimmed = value.strip();
        final String date = trimmed.length() > DATE_LENGTH && !Character.isDigit(trimmed.charAt(DATE_LENGTH))
                ? trimmed.substring(0, DATE_LENGTH)
                : trimmed;
        for (final DateTimeFormatter format : DATES) {
            try {
                return LocalDate.parse(date, format);
            } catch (final DateTimeParseException e) {
                // следующий формат
            }
        }
        return null;
    }

    /** «Покупка», «Купля», «buy», «B» - покупка; «Продажа», «sell», «S» - продажа; иначе null */
    public static ImportKind kind(final String value) {
        final String normalized = header(value);
        if (normalized.startsWith("покуп") || normalized.startsWith("купл") || "buy".equals(normalized)
                || "b".equals(normalized)) {
            return ImportKind.BUY;
        }
        if (normalized.startsWith("продаж") || "sell".equals(normalized) || "s".equals(normalized)) {
            return ImportKind.SELL;
        }
        return null;
    }

    /** ISIN по формату (12 символов: страна, код, контрольная цифра) */
    public static boolean isIsin(final String value) {
        return value != null && ISIN.matcher(value.strip().toUpperCase(Locale.ROOT)).matches();
    }

    /** Значение без пробелов по краям; пусто - null */
    public static String trimmed(final String value) {
        if (value == null) {
            return null;
        }
        final String stripped = SPACES.matcher(value).replaceAll(" ").strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /** SHA-256 в hex - ключ строки без номера сделки: стабилен при повторном импорте того же файла */
    public static String sha256(final String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
