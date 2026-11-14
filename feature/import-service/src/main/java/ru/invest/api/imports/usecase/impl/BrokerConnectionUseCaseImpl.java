package ru.invest.api.imports.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.entity.BrokerConnection;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.account.PlanFeature;
import ru.invest.api.common.repository.BrokerConnectionRepository;
import ru.invest.api.common.usecase.EntitlementUseCase;
import ru.invest.api.imports.crypto.TokenCipher;
import ru.invest.api.imports.model.BrokerConnectionModel;
import ru.invest.api.imports.tinvest.TinvestAccount;
import ru.invest.api.imports.tinvest.TinvestClient;
import ru.invest.api.imports.tinvest.TinvestClientException;
import ru.invest.api.imports.usecase.BrokerConnectionUseCase;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrokerConnectionUseCaseImpl implements BrokerConnectionUseCase {
    static final String TINVEST = "TINVEST";
    // токен T-Invest - печатные ASCII без пробелов (идёт в заголовок Authorization)
    private static final Pattern TOKEN = Pattern.compile("[\\x21-\\x7E]{20,500}");
    private static final int HINT_LENGTH = 4;

    private final BrokerConnectionRepository repository;
    private final TokenCipher tokenCipher;
    private final TinvestClient tinvestClient;
    private final EntitlementUseCase entitlementUseCase;

    @Override
    @Transactional(readOnly = true)
    public BrokerConnectionModel get(final String userId) {
        return repository.findByUserIdAndProvider(userId, TINVEST)
                .map(connection -> toModel(connection, null))
                .orElseGet(() -> new BrokerConnectionModel(tokenCipher.isConfigured(), false, null, null, null, null));
    }

    @Override
    @Transactional
    public BrokerConnectionModel connect(final String userId, final String token) {
        entitlementUseCase.requireFeature(userId, PlanFeature.BROKER_IMPORT);
        requireEnabled();
        final String trimmed = token == null ? "" : token.strip();
        if (!TOKEN.matcher(trimmed).matches()) {
            throw unprocessable("Вставьте токен T-Invest API целиком: латинские буквы, цифры и знаки без пробелов");
        }
        final List<TinvestAccount> accounts = readOnlyAccounts(() -> tinvestClient.getAccounts(trimmed));
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final BrokerConnection connection = repository.findByUserIdAndProvider(userId, TINVEST)
                .orElseGet(() -> new BrokerConnection().setUserId(userId).setProvider(TINVEST).setCreatedAt(now));
        if (connection.getId() != null) {
            connection.setUpdatedAt(now).setCreatedAt(now).setLastImportAt(null);
        }
        connection.setTokenEncrypted(tokenCipher.encrypt(trimmed, userId))
                .setTokenHint(trimmed.substring(trimmed.length() - HINT_LENGTH));
        repository.save(connection);
        log.info("Broker token connected: userId={}, accounts={}", userId, accounts.size());
        return toModel(connection, accounts);
    }

    @Override
    @Transactional
    public void disconnect(final String userId) {
        repository.findByUserIdAndProvider(userId, TINVEST).ifPresent(repository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TinvestAccount> getAccounts(final String userId) {
        final String token = token(userId);
        return readOnlyAccounts(() -> tinvestClient.getAccounts(token));
    }

    /**
     * Расшифрованный токен пользователя - только для запросов к T-Invest.
     *
     * @throws GeneralUnprocessableEntityException импорт выключен, токен не подключён или не расшифровывается
     */
    public String token(final String userId) {
        requireEnabled();
        final BrokerConnection connection = repository.findByUserIdAndProvider(userId, TINVEST)
                .orElseThrow(() -> unprocessable("Сначала подключите токен T-Invest API"));
        try {
            return tokenCipher.decrypt(connection.getTokenEncrypted(), userId);
        } catch (final TokenCipher.TokenDecryptionException e) {
            throw unprocessable("Сохранённый токен не прочитать - подключите токен заново");
        }
    }

    /** Отметка о загрузке сделок по токену */
    @Transactional
    public void markImported(final String userId) {
        repository.findByUserIdAndProvider(userId, TINVEST)
                .ifPresent(connection -> connection.setLastImportAt(LocalDateTime.now(ZoneOffset.UTC)));
    }

    /**
     * Счета токена с проверкой, что токен только на чтение: хоть один счёт с полным доступом - токен не принимается.
     */
    static List<TinvestAccount> readOnlyAccounts(final Supplier<List<TinvestAccount>> request) {
        final List<TinvestAccount> accounts;
        try {
            accounts = request.get();
        } catch (final TinvestClientException e) {
            if (e.getKind() == TinvestClientException.Kind.UNAUTHENTICATED) {
                throw unprocessable("Токен не подошёл: проверьте, что он скопирован целиком и не отозван");
            }
            if (e.getKind() == TinvestClientException.Kind.PERMISSION_DENIED) {
                throw unprocessable("У токена нет доступа к счетам");
            }
            throw e;
        }
        if (accounts.stream().anyMatch(account -> account.access() == TinvestAccount.Access.FULL_ACCESS)) {
            throw unprocessable("У токена есть права на торговлю. Для импорта создайте в Т-Инвестициях токен "
                    + "«Только для чтения» - с ним никто не сможет совершать сделки от вашего имени");
        }
        final List<TinvestAccount> readable = accounts.stream()
                .filter(account -> account.access() == TinvestAccount.Access.READ_ONLY)
                .toList();
        if (readable.isEmpty()) {
            throw unprocessable("Токену не доступен ни один счёт");
        }
        return readable;
    }

    private void requireEnabled() {
        if (!tokenCipher.isConfigured()) {
            throw unprocessable("Импорт по токену пока недоступен - загрузите файл отчёта брокера");
        }
    }

    private BrokerConnectionModel toModel(final BrokerConnection connection, final List<TinvestAccount> accounts) {
        return new BrokerConnectionModel(tokenCipher.isConfigured(), true, connection.getTokenHint(),
                connection.getCreatedAt(), connection.getLastImportAt(), accounts);
    }

    static GeneralUnprocessableEntityException unprocessable(final String message) {
        return new GeneralUnprocessableEntityException(ExceptionErrorCode.IMPORT_INVALID, message);
    }
}
