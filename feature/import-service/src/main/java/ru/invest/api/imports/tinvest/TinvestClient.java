package ru.invest.api.imports.tinvest;

import java.time.Instant;
import java.util.List;

/**
 * T-Invest API с токеном пользователя (а не с токеном приложения, которым синхронизируются облигации). Каждый вызов -
 * своё соединение с токеном в заголовке; токен нигде не сохраняется и не логируется.
 *
 * @throws TinvestClientException у всех методов: API отказал или не ответил
 */
public interface TinvestClient {
    /** Счета, доступные токену, с правами токена на каждый */
    List<TinvestAccount> getAccounts(String token);

    /**
     * Исполненные покупки и продажи ценных бумаг на счёте, старые первыми; комиссия, списанная отдельной операцией,
     * приложена к своей сделке.
     *
     * @param from          с какого момента; null - вся история
     * @param maxOperations больше не читать
     */
    List<TinvestOperation> getTrades(String token, String accountId, Instant from, int maxOperations);

    /** Облигации на счёте сейчас */
    List<TinvestPosition> getBondPositions(String token, String accountId);
}
