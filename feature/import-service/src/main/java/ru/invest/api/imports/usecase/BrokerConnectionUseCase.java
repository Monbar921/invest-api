package ru.invest.api.imports.usecase;

import ru.invest.api.imports.model.BrokerConnectionModel;
import ru.invest.api.imports.tinvest.TinvestAccount;

import java.util.List;

/**
 * Токен T-Invest пользователя для импорта: только на чтение, хранится зашифрованным. Ни в ответы, ни в лог токен не
 * попадает - только последние 4 символа.
 */
public interface BrokerConnectionUseCase {
    /** Подключён ли токен; T-Invest не запрашивается */
    BrokerConnectionModel get(String userId);

    /**
     * Проверяет токен в T-Invest и сохраняет зашифрованным (прежний заменяется).
     *
     * @return подключение со счетами токена
     * @throws ru.invest.api.common.exception.GeneralPaymentRequiredException     импорта нет в тарифе
     * @throws ru.invest.api.common.exception.GeneralUnprocessableEntityException импорт по токену выключен, токен
     *                                                                            пустой, не подошёл, с правами на
     *                                                                            торговлю или без доступных счетов
     * @throws ru.invest.api.imports.tinvest.TinvestClientException              T-Invest не ответил (502)
     */
    BrokerConnectionModel connect(String userId, String token);

    /** Удаляет токен; если его не было - ничего не делает */
    void disconnect(String userId);

    /**
     * Счета подключённого токена - заодно проверяет, что он всё ещё только на чтение.
     *
     * @throws ru.invest.api.common.exception.GeneralUnprocessableEntityException токен не подключён или не подходит
     */
    List<TinvestAccount> getAccounts(String userId);
}
