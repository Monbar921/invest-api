# INVEST-001: Telegram-бот - устройство

```
UI ──▶ invest-gateway (Keycloak, сессия) ──X-User-Id──▶ invest-api /internal/rest/alerts/**
                                                         │
          шедулеры цен, купонов, новостей ──события──▶ alert-service ──▶ notification (очередь)
                                                         │                      │
                                                         │            NotificationSender
                                                         ▼                      ▼
                                  telegram-bot (long polling) ◀──▶ sing-box ──▶ VPS ──▶ api.telegram.org
```

## Пользователь

В invest-api своей авторизации нет и не появляется (см. [architecture.md](../../architecture.md)). Пользователя
определяет invest-gateway:

- фильтр gateway берёт `sub` из OIDC-пользователя сессии и ставит заголовок `X-User-Id`; входящий заголовок с тем же
  именем gateway **удаляет**, чтобы его нельзя было подделать из браузера;
- новый маршрут `/api/alerts/**` → `/internal/rest/alerts/**` (сейчас наружу проходит только `/api/bonds/**`);
- контроллеры уведомлений без `X-User-Id` отвечают `401`. invest-api по-прежнему доступен только через gateway.

`user_id` - строка (`sub` Keycloak), отдельной таблицы пользователей нет.

## Привязка Telegram

1. UI: «Подключить Telegram» → `POST /alerts/telegram/link-token` → `{ url: "https://t.me/<bot>?start=<token>" }`.
2. Токен - 32 случайных байта (base64url), в БД хранится SHA-256, живёт 10 минут, одноразовый.
3. Пользователь открывает ссылку, Telegram присылает боту `/start <token>`; бот находит токен, сохраняет
   `chat_id` для `user_id`, удаляет токен и отвечает «Подключено».
4. Повторная привязка заменяет чат. `DELETE /alerts/telegram` - отвязать.
5. Если Telegram на отправку отвечает `403` (пользователь заблокировал бота) - привязка удаляется, в UI статус
   «Не подключён».

## Правила

`bond_alert` - одно правило на строку; у облигации может быть несколько правил.

| Тип | Параметры | Когда срабатывает |
| --- | --- | --- |
| `PRICE_LEVEL` | `direction` ABOVE/BELOW, `value` % от номинала | цена пересекла уровень |
| `PRICE_CHANGE` | `direction` UP/DOWN/ANY, `percent`; `base_price` - цена при создании | цена отклонилась от `base_price` на `percent` относительно; одноразовое |
| `PRICE_DAILY_CHANGE` | `direction`, `percent` | цена отклонилась от закрытия прошлого торгового дня (последняя дневная свеча до сегодня); не чаще раза в день |
| `YTM_LEVEL` | `direction`, `value` % | доходность к погашению пересекла уровень |
| `YTO_LEVEL` | `direction`, `value` % | доходность к оферте пересекла уровень |
| `COUPON_BEFORE` | `days` | до ближайшего купона `days` дней (размер купона - в сообщении, если объявлен) |
| `OFFER_BEFORE` | `days` | до оферты (`callDate`) `days` дней |
| `MATURITY_BEFORE` | `days` | до погашения `days` дней |
| `AMORTIZATION_BEFORE` | `days` | до частичного погашения номинала `days` дней |
| `FLOATER_COUPON` | - | у флоатера появился размер следующего купона |
| `BOND_NEWS` | - | новая новость или событие, привязанные к облигации |
| `KEY_RATE` | - | ЦБ изменил ключевую ставку; правило пользователя, не облигации |

Хранение (этап 2): колонки `user_id`, `ticker`, `type`, `days`, `enabled`, `created_at`, `last_triggered_at`;
облигация - по тикеру, без внешнего ключа. Параметры порогов этапа 3 (`direction`, `value`, `base_price`) добавятся
колонками: `jsonb` отвергнут - Hibernate 7 маппит JSON через Jackson, а в проекте Jackson 3, это лишняя настройка ради
трёх полей. Ограничения: `days` 1..60, не больше 50 правил на пользователя, одно правило каждого типа на облигацию.

### Пересечение, а не состояние

Пороговые правила (`*_LEVEL`) хранят `state`: `ARMED` → при пересечении уведомление и `TRIGGERED` → снова `ARMED`,
когда значение вернётся за порог с запасом (гистерезис: 0,2 п.п. для цены, 0,1 п.п. для доходности). Так колебания
около уровня не дают поток сообщений. При создании правила, если условие уже выполнено, правило сразу `TRIGGERED`
и одно уведомление «уже сейчас ниже/выше».

`PRICE_CHANGE` после срабатывания выключается (`enabled = false`), пользователь может создать новое. Правила по
датам срабатывают один раз на каждую дату выплаты.

Облигация удалена синхронизацией (погашена, ушла с биржи) - её правила удаляются вместе с ней, перед этим одно
уведомление «облигация больше не торгуется».

## Когда проверяются правила

| Правила | Когда |
| --- | --- |
| цена, доходность | после шага `price` ночной цепочки и после каждого запуска `PriceSyncScheduler` |
| даты | ежедневно в 09:00 МСК (`AlertDateScheduler`), не ночью |
| `FLOATER_COUPON` | после шага `coupon` |
| `BOND_NEWS`, `KEY_RATE` | после загрузки новостей |

Шедулеры синхронизации публикуют Spring-события (`PricesSyncedEvent`, `CouponsSyncedEvent`, `NewsSyncedEvent`),
`alert-service` их слушает (`@TransactionalEventListener` после коммита, в своём пуле): модули синхронизации об
уведомлениях не знают.

Для цен нужен включённый `PriceSyncScheduler` в торговые часы: предложено каждые 5 минут (`GetLastPrices` - один
запрос на пачку облигаций, лимит MarketData 600 в минуту). Проверяются только облигации, на которые есть правила.

## Доставка: очередь уведомлений

Telegram может быть недоступен (блокировка, прокси не запущен), поэтому проверка правил и отправка разделены.

`notification`: `id`, `user_id`, `alert_id`, `dedup_key` (уникальный), `text`, `status` PENDING / SENT / FAILED,
`attempts`, `next_attempt_at`, `sent_at`, `last_error`.

- Сработавшее правило пишет строку в той же транзакции, что меняет `state` правила.
- `dedup_key` не даёт повторов при перезапуске и повторной синхронизации: `COUPON_BEFORE:<alertId>:<дата купона>`,
  `PRICE_DAILY_CHANGE:<alertId>:<дата>`, `PRICE_LEVEL:<alertId>:<номер срабатывания>` и т.п. Вставка с
  `on conflict do nothing`.
- `NotificationDispatcher` раз в 30 секунд (ShedLock `NotificationDispatch`) берёт `PENDING` с
  `next_attempt_at <= now()` и отправляет через `NotificationSender`. Успех - `SENT`. Ошибка сети - повтор через 1, 2,
  4 ... 30 минут. Через сутки без успеха - `FAILED`. Ответ `403` - привязка удаляется, уведомления пользователя `FAILED`.
- Лимиты Telegram (около 30 сообщений в секунду всего и 1 в секунду в один чат) при наших объёмах не мешают;
  на `429` - ждать `retry_after`.
- Отправленные старше 90 дней удаляются.

`NotificationSender` - интерфейс; первая реализация - Telegram. Второй канал (email, web push) добавляется без
изменений в `alert-service`.

## Бот

Библиотека `telegrambots` (long polling starter + OkHttp-клиент). **Long polling, а не webhook**: у ПК нет публичного
HTTPS-адреса. Один и тот же `OkHttpClient` с прокси из `ru.invest.api.telegram.proxy` отдаётся и long polling, и
клиенту отправки ([proxy.md](proxy.md)). Long polling должен работать в одном экземпляре приложения.

| Команда | Что |
| --- | --- |
| `/start <токен>` | привязка чата; без токена - подсказка «подключите бота из UI» |
| `/list` | правила пользователя по облигациям, у каждого кнопка «Отключить» |
| `/stop` | отвязать чат |

Сообщение уведомления: облигация (название, тикер), что произошло, текущие цена, доходность, дата; кнопки
«Отключить правило» (`callback_data = off:<alertId>`) и «Открыть в UI» (ссылка на карточку). Обработчик кнопки
проверяет, что правило принадлежит пользователю этого чата. Текст - HTML parse mode с экранированием.

`ru.invest.api.telegram.enabled: false` - бот и отправка выключены, правила и очередь работают (удобно для тестов
и запуска без прокси).

## REST (invest-api)

| Метод | Что |
| --- | --- |
| `GET /internal/rest/alerts` | правила пользователя с облигациями |
| `GET /internal/rest/alerts?ticker=` | правила по облигации (для диалога) |
| `POST /internal/rest/alerts` | создать правило |
| `PUT /internal/rest/alerts/{id}` | изменить параметры, включить/выключить |
| `DELETE /internal/rest/alerts/{id}` | удалить |
| `GET /internal/rest/alerts/telegram` | статус: подключён ли чат, имя, доступен ли Telegram (последняя попытка связи) |
| `POST /internal/rest/alerts/telegram/link-token` | ссылка для привязки |
| `DELETE /internal/rest/alerts/telegram` | отвязать |
| `GET /internal/rest/alerts/notifications?page=` | история отправленных |

Все - с `X-User-Id`; чужое правило - `404`.

## UI

- Колокольчик в строке таблицы и в карточке облигации; заполненный, если на облигацию есть правила.
- Диалог: список правил облигации, добавление по типу с полями параметров, переключатель вкл/выкл. Если Telegram не
  подключён - сверху кнопка «Подключить Telegram».
- Страница «Мои уведомления»: все правила по облигациям, статус Telegram, история.

## Сделано на этапе 1

- `feature/telegram-bot`: `TelegramProperties` (`ru.invest.api.telegram.*`), `TelegramClientConfiguration` (OkHttp с
  прокси, `OkHttpTelegramClient`), `handler/TelegramBotRunner` (long polling, меню команд), `TelegramUpdateHandler`,
  `TelegramMessageSender`, `status/TelegramAvailability` и `TelegramAvailabilityChecker`, `TelegramLinkUseCase`.
  Библиотека `telegrambots` 10.3.0 (`telegrambots-longpolling` + `telegrambots-client`) без Spring Boot стартера:
  стартер библиотеки рассчитан на Boot 3, жизненным циклом управляет свой `SmartLifecycle`.
- `ru.invest.api.telegram.polling: false` - бот включён, но long polling и проверка связи не запускаются (тесты).
- Сущности `TelegramLink`, `TelegramLinkToken` и репозитории - в `common`, как остальные; миграция
  `tables/008-telegram.xml`.
- `ui-service`: `TelegramController` (`/internal/rest/alerts/telegram`), `TelegramDtoMapper`; `401` для запросов без
  `X-User-Id` - `GeneralUnauthorizedException`.
- invest-gateway: `UserIdHeaderFilter` (после цепочки Spring Security), маршрут `/api/alerts/**`.
- UI: кнопка Telegram в шапке (`TelegramConnect`): статус запрашивается при открытии панели, после выдачи ссылки -
  опрос раз в 3 секунды, пока не появится привязка. Ссылка открывается кнопкой, а не `window.open` после запроса:
  открытие окна не из обработчика клика блокируют браузеры.

## Сделано на этапе 2

- `feature/alert-service`: `AlertUseCase` (правила, история), `AlertCheckUseCase` (окно «сегодня + days», ключ
  `тип:правило:дата`, облигации больше нет - удаление правил и одно уведомление), `NotificationDispatchUseCase`
  (разбор очереди, повторы, отказ через сутки, чистка через 90 дней), `NotificationSender` / `DeliveryResult`,
  `AlertScheduler` (09:00 МСК и при старте), `NotificationDispatchScheduler` (30 с). Тексты - `AlertTexts`.
- Правила проверяются заново из БД внутри транзакции облигации, наличие облигации - через репозиторий, а не через
  исключение «не найдена»: исключение из транзакционного метода пометило бы транзакцию на откат.
- `telegram-bot`: `TelegramNotificationSender` (кнопка `alert-off:<id>`; 403 - отвязка, 429 - `retry_after`, 400 -
  отказ, остальное - повтор), `/list` и обработка кнопки в `TelegramUpdateHandler`.
- `ui-service`: `AlertController`, `AlertDtoMapper`, `UserIdHeader`; `400` для невалидного тела запроса.
- UI: колокольчик `BondAlertsButton` в строке таблицы и в карточке (все правила одним запросом), `BondAlertsPanel`
  (переключатель и дни на каждый тип), страница `/alerts` «Уведомления» с историей.

## Сделано на этапе 3

- Типы `PRICE_LEVEL`, `PRICE_CHANGE`, `PRICE_DAILY_CHANGE`, `YTM_LEVEL`, `YTO_LEVEL`; колонки `direction`,
  `threshold`, `base_value`, `state`, `trigger_count` (миграция `010-alert-price.xml`); уникальность только у правил
  по датам - частичный индекс.
- `PriceAlertCheckUseCaseImpl`: значения - из кэша облигаций, закрытие прошлого дня - `PriceCandleRepository.findLastBefore`.
  Ключ против повторов: у уровней и изменения от цены - `тип:правило:номер срабатывания`, у изменения за день -
  `тип:правило:дата`.
- Синхронизация цен публикует `PricesSyncedEvent` после сброса кэша; `PriceAlertListener` проверяет правила, ошибки
  только в лог - синхронизация цен от уведомлений не зависит.
- Создание и изменение правил - `AlertRuleModel` / `AlertChangeModel`; пределы порогов проверяются в `AlertUseCaseImpl` (`422`).
- UI: раздел «Цена и доходность» в окне колокольчика - список порогов текстом, переключатель, удаление и форма добавления
  (тип, направление, порог) с текущими ценой и доходностями.

## Сделано на этапе 4

- Типы `FLOATER_COUPON`, `BOND_NEWS`, `ISSUER_RATING`, `KEY_RATE`; колонки `cursor_id`, `cursor_date`, `ticker`
  необязателен (миграция `011-alert-events.xml`).
- `NewsAlertCheckUseCaseImpl`: новости облигации - `NewsUseCase.getByTicker`, ключевая ставка - лента `CBR_PRESS` с
  заголовком «снизил / повысил / сохранил ключевую ставку». Новости читаются до транзакций правил.
  Ключ - `тип:правило:id новости`.
- Ключевая ставка - по пресс-релизу, а не по значению ставки: значение меняется со следующего рабочего дня после
  заседания, и в выходные между ними сравнение «до/после» показало бы «сохранил».
- Объявленный купон - в `AlertCheckUseCaseImpl` (утренняя проверка), по событиям `BondEventUseCase` с размером и прошлым
  размером.
- UI: раздел «События и новости» в окне колокольчика, карточка «Банк России» на странице уведомлений.

## Модули

| Модуль | Что |
| --- | --- |
| `feature/alert-service` | сущности и репозитории правил и очереди, проверка правил, слушатели событий синхронизации, `AlertDateScheduler`, `NotificationDispatcher` |
| `feature/telegram-bot` | конфиг клиента с прокси, long polling, команды и кнопки, `TelegramNotificationSender`, привязка |
| `feature/ui-service` | `AlertController`, `TelegramLinkController` |
| `invest-api-dto` | DTO правил и уведомлений |
| `app` | Liquibase-миграции четырёх таблиц |

## Решения и отвергнутые варианты

- **Webhook** - нужен публичный HTTPS, у ПК его нет. Long polling работает за NAT и через прокси.
- **VPN на весь ПК** - T-Invest, Мосбиржа и ЦБ пошли бы через зарубежный IP. Прокси только у клиента Telegram.
- **Свой Telegram Bot API server на VPS** - тоже нужен VPS, но больше поддержки, чем у прокси.
- **Бот на VPS** - ПК недоступен извне, боту пришлось бы забирать уведомления у invest-api; сложнее без выгоды.
- **Отдельный сервис уведомлений** - все данные для правил уже в invest-api; выделить можно позже, интерфейс
  `NotificationSender` это позволяет.
- **Настройка правил в боте** - в первой версии только в UI, бот только показывает и отключает.
- **Проверка по состоянию** («цена ниже 95%» каждый раз) - спам; используются пересечения с гистерезисом.

## Тесты (план)

- Юнит: проверка каждого типа правил, гистерезис, расчёт дат, `dedup_key`.
- Интеграционные (Testcontainers): создание правил через REST с `X-User-Id`, срабатывание после события
  синхронизации, очередь с фейковым `NotificationSender` (ошибка → повтор, `403` → отвязка).
- Бот: обработчики команд и кнопок без сети, клиент Telegram замокан.
