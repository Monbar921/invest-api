package ru.invest.api.alert.service.usecase;

import java.time.LocalDate;

/**
 * Проверка правил по датам: за days дней до события облигации - уведомление в очередь, один раз на каждое событие.
 */
public interface AlertCheckUseCase {
    /**
     * Все включённые правила. Облигации, которой больше нет, - уведомление и удаление её правил.
     *
     * @param today «сегодня» в часовом поясе событий (Москва)
     * @return сколько уведомлений поставлено в очередь
     */
    int checkAll(LocalDate today);

    /**
     * Одно правило - сразу после создания или изменения, чтобы событие внутри окна не ждало утренней проверки.
     */
    int check(Long alertId, LocalDate today);
}
