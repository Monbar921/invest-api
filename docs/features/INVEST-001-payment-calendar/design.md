# INVEST-001: календарь выплат - устройство

```
UI /portfolio?tab=payments ──▶ gateway /api/portfolio/calendar ──X-User-Id──▶ invest-api /internal/rest/portfolio/calendar
Google / Apple / Outlook   ──▶ gateway /api/calendar/<токен>.ics (без сессии) ──▶ invest-api /internal/rest/calendar/<токен>.ics
                                                                                   │
                                        PortfolioCalendarUseCase (portfolio-service)
                                          ├─ portfolio_position          количество по тикеру
                                          ├─ BondUseCase.getWithSchedule график купонов и амортизации
                                          ├─ BondEventUseCase            события (как у карточки)
                                          ├─ PriceConversionService      курс к рублю на сегодня
                                          └─ CalendarIcsWriter           iCalendar
```

## Модули

- `feature/portfolio-service` - `PortfolioCalendarUseCase` / `PortfolioCalendarUseCaseImpl`, `CalendarIcsWriter`,
  модели `PortfolioCalendarModel`, `CalendarEventModel`, `CalendarMonthModel`, `CalendarTotalModel`,
  `CalendarLinkModel`. Отдельный use case, а не методы `PortfolioUseCase`: у календаря свои зависимости (события,
  токены, iCalendar), а общая у них только таблица покупок.
- `common` - сущность `PortfolioCalendarToken`, `PortfolioCalendarTokenRepository`.
- `ui-service` - эндпоинты календаря в `PortfolioController`, `CalendarFeedController` (`.ics`), маппинг в
  `PortfolioDtoMapper` (`toCalendarDto`, `toLinkDto`).
- `invest-api-dto` - `PortfolioCalendarDto`, `PortfolioCalendarEventDto`, `PortfolioCalendarMonthDto`,
  `PortfolioCalendarTotalDto`, `CalendarLinkDto`.

## API

| Метод и путь | Что |
| --- | --- |
| `GET /internal/rest/portfolio/calendar?from=&to=` | календарь: `from` не задан или в прошлом - сегодня, `to` не задан - год от `from`; `to` раньше `from` - `422` |
| `GET /internal/rest/portfolio/calendar/link` | `{enabled, token: null, createdAt}` |
| `POST /internal/rest/portfolio/calendar/link` | новая ссылка `{enabled: true, token, createdAt}`; прежняя перестаёт работать |
| `DELETE /internal/rest/portfolio/calendar/link` | отключить (`204`, в том числе если ссылки не было) |
| `GET /internal/rest/calendar/{token}.ics` | `text/calendar; charset=UTF-8`, `Cache-Control: no-store`; неизвестный токен - `404` |

Адрес подписки строит UI: `<origin>/api/calendar/<токен>.ics`. invest-api своего внешнего адреса не знает, а UI и
gateway - на одном origin.

## БД

`portfolio_calendar_token` (миграция `tables/015-portfolio-calendar.xml`): `user_id` - первичный ключ (одна ссылка на
пользователя), `token_hash` (SHA-256 hex, уникален), `created_at` (UTC). Новая ссылка перезаписывает строку
пользователя - прежний токен перестаёт находиться.

## Расчёт

1. Покупки пользователя складываются по тикеру. Покупки - не позже сегодня, события - с сегодня, поэтому на дату
   любой выплаты в портфеле все покупки облигации, и количество одно на весь период.
2. Какие облигации есть - одним запросом `BondUseCase.getByTickers`; для них - `getWithSchedule` (график купонов и
   амортизации), остальные - в `unknownTickers`. Сначала проверка, а не перехват `404`: исключение «облигации нет»
   из транзакционного метода внутри read-only транзакции календаря пометило бы её на откат (`UnexpectedRollbackException`).
3. `BondEventUseCase.getEvents(bond, from)`, затем отсечение по `to`. Сумма = выплата на облигацию × количество,
   рубли - по курсу на сегодня (`PriceConversionService.getRubRate`, валюта - один запрос на календарь).
4. Будет ли выплата у события без суммы (`expectsPayment`): у оферты - нет; у погашения - нет, если в тот же день
   есть амортизация (остаток номинала в ней, так считает `BondEventUseCase`); у купона и амортизации - да, значит
   размер не объявлен - `amountUnknown`.
5. Итоги по валютам (в порядке первой выплаты), по месяцам (`YYYY-MM`, только месяцы с событиями) и за период;
   рубли итога = сумма валюты × курс, всё в рублях - `null`, если у какой-то валюты нет курса.

## iCalendar

`CalendarIcsWriter`: `VCALENDAR` с `X-WR-CALNAME`, `REFRESH-INTERVAL` / `X-PUBLISHED-TTL` 12 часов (размеры купонов
флоатеров объявляются по ходу). На каждое событие - `VEVENT` на весь день (`DTSTART;VALUE=DATE`, `DTEND` - следующий
день), `UID` = `<тикер>-<тип>-<дата>@invest-api` - постоянный, поэтому при обновлении подписки календарь меняет
событие, а не создаёт второе. `SUMMARY` - «Купон: ОФЗ 26238 - 175,75 ₽» (или «размер не объявлен», «без выплаты»,
«в амортизации»), `DESCRIPTION` - тикер и «5 шт. × 35,15 ₽ = 175,75 ₽». Текст экранируется (`\`, `;`, `,`, перевод
строки), строки переносятся по 75 октетов UTF-8, не разрывая символ, переводы строк - CRLF. Горизонт - 50 лет
(все будущие выплаты, длинные ОФЗ гасятся через 15-20 лет).

## UI (invest-api-ui)

`src/components/PortfolioPayments.tsx`, вкладка «Выплаты» в `PortfolioPage` (`Tabs`, вкладка в `?tab=payments`,
`keepMounted={false}` - календарь не запрашивается, пока вкладку не открыли). Запросы - `src/api/portfolio.ts`
(`fetchPortfolioCalendar`, `fetchCalendarLink`, `createCalendarLink`, `deleteCalendarLink`, `calendarFeedUrl`), хуки -
`usePortfolioCalendar(to)`, `useCalendarLink`, `useCalendarLinkMutations`. Ключи календаря внутри `portfolioKeys.all`:
изменение покупок перечитывает и календарь. Период - конец периода `to` (`addMonths` от сегодня), `from` не
передаётся - начало считает бэк. Подтверждение «Новая ссылка» / «Отключить ссылку» - на месте кнопки.

## Решения и отвергнутые варианты

- **Только будущие выплаты.** Прошлые выплаты зависят от того, сколько облигаций было на дату фиксации реестра, и
  это часть полного дохода портфеля (этап 8). Начало периода раньше сегодня заменяется сегодняшним днём.
- **Токен хранится хешем, адрес показывается один раз** - как ссылка привязки Telegram. Ссылка открывает личные
  данные без входа: утечка БД не должна давать готовые ссылки. Потерянный адрес заменяют новой ссылкой (прежняя
  перестаёт работать). Вариант «хранить токен и показывать всегда» отвергнут.
- **Одна ссылка на пользователя.** Несколько ссылок (на каждый календарь) усложнили бы UI ради редкого случая;
  отключение и замена закрывают утечку.
- **Публичный маршрут в gateway, а не сессия.** Календари забирают подписку со своих серверов без cookie. Маршрут
  открыт независимо от публичного режима: это не общие данные, их защищает токен. Анонимный лимит запросов gateway
  на него действует.
- **Событие без суммы вместо «примерной» суммы.** Размер будущего купона флоатера неизвестен; подставить последний
  объявленный значило бы показать приблизительную величину как точную. Итог помечается неполным, UI пишет «от …».
- **Погашение - как в карточке.** Сумма погашения - текущий номинал, если в день погашения нет амортизации
  (`BondEventUseCase`). Если у облигации будущие частичные погашения не доходят до даты погашения, а последняя часть
  номинала в графике амортизации не указана, погашение покажет текущий номинал, а не остаток. Так же сейчас в
  карточке; исправлять - в `BondEventUseCase` для обоих сразу.
- **Рубли - по курсу на сегодня**, как текущая стоимость портфеля: курс на дату будущей выплаты неизвестен.
