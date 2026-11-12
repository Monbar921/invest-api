package ru.invest.api.ui.service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.portfolio.service.usecase.PortfolioCalendarUseCase;

import java.nio.charset.StandardCharsets;

/**
 * Календарь выплат портфеля в формате iCalendar по личной ссылке - для подписки из Google, Apple, Outlook и других
 * календарей, которые ходят без сессии. Пользователь определяется токеном, X-User-Id не нужен. Наружу через gateway:
 * публичный маршрут /api/calendar/** → /internal/rest/calendar/**.
 */
@RestController
@RequestMapping("/internal/rest/calendar")
@RequiredArgsConstructor
public class CalendarFeedController {
    private static final MediaType TEXT_CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final PortfolioCalendarUseCase portfolioCalendarUseCase;

    /**
     * Все будущие выплаты по портфелю владельца ссылки. Неизвестный или отключённый токен - 404.
     */
    @GetMapping("/{token}.ics")
    public ResponseEntity<String> getIcs(@PathVariable final String token) {
        return ResponseEntity.ok()
                .contentType(TEXT_CALENDAR)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"bond-payments.ics\"")
                // личные данные: прокси и браузер не кэшируют, календарь перечитывает подписку сам
                .cacheControl(CacheControl.noStore())
                .body(portfolioCalendarUseCase.getIcs(token));
    }
}
