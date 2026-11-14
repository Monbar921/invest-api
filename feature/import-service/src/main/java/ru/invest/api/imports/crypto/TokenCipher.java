package ru.invest.api.imports.crypto;

import org.springframework.stereotype.Component;
import ru.invest.api.imports.config.ImportProperties;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Шифрование токенов брокера: AES-256-GCM, случайный IV на каждое шифрование, пользователь - дополнительные данные
 * (AAD): шифртекст, скопированный другому пользователю, не расшифруется. Формат: {@code v1:<Base64(IV + шифртекст)>}.
 * Ни токен, ни ключ в сообщения исключений не попадают.
 */
@Component
public class TokenCipher {
    private static final String VERSION = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(final ImportProperties properties) {
        this.key = properties.isTokenImportEnabled()
                ? new SecretKeySpec(Base64.getDecoder().decode(properties.getTokenEncryptionKey().strip()), "AES")
                : null;
    }

    /** Ключ задан - токены можно шифровать */
    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(final String token, final String userId) {
        requireKey();
        try {
            final byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(userId.getBytes(StandardCharsets.UTF_8));
            final byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return VERSION + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("Token encryption failed: " + e.getClass().getSimpleName());
        }
    }

    /**
     * @throws TokenDecryptionException ключ сменился, данные повреждены или принадлежат другому пользователю
     */
    public String decrypt(final String value, final String userId) {
        requireKey();
        if (value == null || !value.startsWith(VERSION)) {
            throw new TokenDecryptionException();
        }
        try {
            final byte[] data = Base64.getDecoder().decode(value.substring(VERSION.length()));
            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            cipher.updateAAD(userId.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (final GeneralSecurityException | IllegalArgumentException e) {
            throw new TokenDecryptionException();
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("Token encryption key is not configured");
        }
    }

    /** Токен не расшифровать - его нужно подключить заново */
    public static class TokenDecryptionException extends RuntimeException {
        public TokenDecryptionException() {
            super("Stored broker token cannot be decrypted");
        }
    }
}
