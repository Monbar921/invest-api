package ru.invest.api.portfolio.service.usecase;

import ru.invest.api.portfolio.service.model.CalendarLinkModel;
import ru.invest.api.portfolio.service.model.PortfolioCalendarModel;

import java.time.LocalDate;

/**
 * Календарь выплат портфеля: будущие купоны, амортизация, оферты и погашения по облигациям пользователя, и личная
 * ссылка на него в формате iCalendar (.ics) для подписки из календаря без входа.
 */
public interface PortfolioCalendarUseCase {
    /**
     * @param from с какой даты; не задана или в прошлом - с сегодня
     * @param to   по какую дату включительно; не задана - год от from
     * @throws ru.invest.api.common.exception.GeneralUnprocessableEntityException to раньше from
     */
    PortfolioCalendarModel getCalendar(String userId, LocalDate from, LocalDate to);

    /**
     * Есть ли у пользователя ссылка; токен не отдаётся - он не хранится.
     */
    CalendarLinkModel getLink(String userId);

    /**
     * Новая ссылка; прежняя перестаёт работать.
     */
    CalendarLinkModel createLink(String userId);

    /**
     * Отключить ссылку; если её не было - ничего не происходит.
     */
    void deleteLink(String userId);

    /**
     * Календарь в формате iCalendar по токену ссылки: все будущие выплаты.
     *
     * @throws ru.invest.api.common.exception.GeneralNotFoundEntityException токен неизвестен или ссылку отключили
     */
    String getIcs(String token);
}
