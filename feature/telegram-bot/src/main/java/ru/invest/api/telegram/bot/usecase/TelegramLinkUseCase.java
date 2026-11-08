package ru.invest.api.telegram.bot.usecase;

import ru.invest.api.telegram.bot.model.LinkResult;
import ru.invest.api.telegram.bot.model.TelegramLinkModel;
import ru.invest.api.telegram.bot.model.TelegramStatusModel;

/**
 * Привязка чата Telegram к пользователю (sub Keycloak).
 */
public interface TelegramLinkUseCase {
    /**
     * Новая одноразовая ссылка привязки; прежние токены пользователя перестают действовать.
     *
     * @throws ru.invest.api.common.exception.GeneralUnprocessableEntityException бот выключен
     */
    TelegramLinkModel createLink(String userId);

    /**
     * /start &lt;токен&gt; из чата: чат привязывается к владельцу токена, токен удаляется. Если чат был привязан
     * к другому пользователю - переходит к новому; прежний чат пользователя отвязывается.
     *
     * @param username @username в Telegram, может быть null
     */
    LinkResult link(String token, Long chatId, String username);

    /**
     * @return true - чат был привязан
     */
    boolean unlinkByUser(String userId);

    /**
     * /stop из чата.
     *
     * @return true - чат был привязан
     */
    boolean unlinkByChat(Long chatId);

    TelegramStatusModel getStatus(String userId);
}
