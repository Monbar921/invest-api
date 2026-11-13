package ru.invest.api.payment.service.provider.tbank;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Подпись Т-Кассы (Token): параметры верхнего уровня без вложенных объектов и самого Token плюс Password, по
 * алфавиту ключей; значения склеиваются без разделителей, от строки - SHA-256 в hex. Так подписываются наши запросы и
 * так провайдер подписывает webhook.
 */
public final class TbankSignature {
    public static final String TOKEN = "Token";
    private static final String PASSWORD = "Password";

    private TbankSignature() {
    }

    public static String token(final Map<String, ?> params, final String password) {
        final Map<String, String> sorted = new TreeMap<>();
        params.forEach((key, value) -> {
            // вложенные объекты (Receipt, DATA) и массивы в подпись не входят
            if (!TOKEN.equals(key) && value != null && !(value instanceof Map) && !(value instanceof Iterable)) {
                sorted.put(key, String.valueOf(value));
            }
        });
        sorted.put(PASSWORD, password);
        return sha256(String.join("", sorted.values()));
    }

    /** Подпись сходится; сравнение за постоянное время */
    public static boolean verify(final Map<String, ?> params, final String password) {
        final Object token = params.get(TOKEN);
        return token instanceof String value && MessageDigest.isEqual(
                value.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                token(params, password).getBytes(StandardCharsets.US_ASCII));
    }

    private static String sha256(final String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
