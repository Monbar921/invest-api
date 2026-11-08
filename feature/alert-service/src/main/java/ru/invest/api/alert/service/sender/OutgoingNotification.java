package ru.invest.api.alert.service.sender;

/**
 * Что отправить пользователю.
 *
 * @param alertId правило; null - уведомление не от правила (кнопку «Отключить» не показывать)
 * @param text    простой текст без разметки
 */
public record OutgoingNotification(Long id, String userId, Long alertId, String ticker, String text) {
}
