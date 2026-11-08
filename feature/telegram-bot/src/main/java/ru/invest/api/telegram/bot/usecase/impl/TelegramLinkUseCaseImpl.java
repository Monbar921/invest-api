package ru.invest.api.telegram.bot.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.entity.TelegramLink;
import ru.invest.api.common.entity.TelegramLinkToken;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.repository.TelegramLinkRepository;
import ru.invest.api.common.repository.TelegramLinkTokenRepository;
import ru.invest.api.telegram.bot.config.TelegramProperties;
import ru.invest.api.telegram.bot.model.LinkResult;
import ru.invest.api.telegram.bot.model.TelegramLinkModel;
import ru.invest.api.telegram.bot.model.TelegramStatusModel;
import ru.invest.api.telegram.bot.status.TelegramAvailability;
import ru.invest.api.telegram.bot.usecase.TelegramLinkUseCase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramLinkUseCaseImpl implements TelegramLinkUseCase {
    // 32 байта в base64url - 43 символа: start-параметр Telegram допускает до 64 символов A-Z a-z 0-9 _ -
    private static final int TOKEN_BYTES = 32;
    private static final String LINK_URL = "https://t.me/%s?start=%s";

    private final SecureRandom secureRandom = new SecureRandom();
    private final TelegramProperties properties;
    private final TelegramLinkRepository linkRepository;
    private final TelegramLinkTokenRepository tokenRepository;
    private final TelegramAvailability availability;

    @Override
    @Transactional
    public TelegramLinkModel createLink(final String userId) {
        if (!properties.isEnabled()) {
            throw new GeneralUnprocessableEntityException(ExceptionErrorCode.TELEGRAM_DISABLED,
                    "Telegram bot is disabled");
        }
        final LocalDateTime now = now();
        tokenRepository.deleteByUserIdOrExpired(userId, now);

        final byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        final String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        final LocalDateTime expiresAt = now.plus(properties.getLinkTokenTtl());
        tokenRepository.save(new TelegramLinkToken()
                .setTokenHash(hash(token))
                .setUserId(userId)
                .setExpiresAt(expiresAt));
        return new TelegramLinkModel(LINK_URL.formatted(properties.getBotUsername(), token), expiresAt);
    }

    @Override
    @Transactional
    public LinkResult link(final String token, final Long chatId, final String username) {
        final Optional<TelegramLinkToken> found = tokenRepository.findById(hash(token));
        if (found.isEmpty()) {
            return LinkResult.INVALID_TOKEN;
        }
        final TelegramLinkToken linkToken = found.get();
        tokenRepository.delete(linkToken);
        if (linkToken.getExpiresAt().isBefore(now())) {
            return LinkResult.INVALID_TOKEN;
        }

        // чат мог быть привязан к другому пользователю (или к этому же): чат - только у одного пользователя
        linkRepository.deleteByChatId(chatId);
        linkRepository.save(new TelegramLink()
                .setUserId(linkToken.getUserId())
                .setChatId(chatId)
                .setUsername(username)
                .setLinkedAt(now()));
        log.info("Telegram chat linked: user={}", linkToken.getUserId());
        return LinkResult.LINKED;
    }

    @Override
    @Transactional
    public boolean unlinkByUser(final String userId) {
        if (!linkRepository.existsById(userId)) {
            return false;
        }
        linkRepository.deleteById(userId);
        log.info("Telegram chat unlinked from UI: user={}", userId);
        return true;
    }

    @Override
    @Transactional
    public boolean unlinkByChat(final Long chatId) {
        return linkRepository.deleteByChatId(chatId) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public TelegramStatusModel getStatus(final String userId) {
        final Optional<TelegramLink> link = linkRepository.findById(userId);
        final TelegramAvailability.State state = availability.get();
        return new TelegramStatusModel(
                properties.isEnabled(),
                properties.getBotUsername(),
                link.isPresent(),
                link.map(TelegramLink::getUsername).orElse(null),
                link.map(TelegramLink::getLinkedAt).orElse(null),
                state == null ? null : state.available(),
                state == null ? null : state.checkedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findUserId(final Long chatId) {
        return linkRepository.findByChatId(chatId).map(TelegramLink::getUserId);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static String hash(final String token) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
