# Архитектура invest-api

Сервис собирает облигации, купоны, амортизацию и цены из T-Invest API, хранит их в PostgreSQL, считает доходности
и отдаёт список облигаций с фильтрами и сортировкой. Отдельно собирает ленту новостей Банка России и Мосбиржи.
Spring Boot 4, Java 25, Maven multi-module.

```
                      ┌──────────────────────── invest-api ────────────────────────┐
T-Invest API (gRPC) ◀─┤ tinkoff-supplier ◀── bond-supplier (шедулеры, BondUseCase)   │
ЦБ РФ (XML, RSS)    ◀─┤ cb-rf-supplier   ◀┐                                         │
api.budjet.org      ◀─┤ budget-org-supp. ◀┴─ currency-service (курсы валют)         │
Мосбиржа ISS (JSON) ◀─┤ news-supplier (новости, эмитенты, ставка и курсы)           │
api.telegram.org    ◀─┤ telegram-bot (long polling, привязка чата; прокси - опционально) │
                      │                                                             │
invest-gateway ──────▶│ ui-service: /internal/rest/bonds/**, /internal/rest/maintenance/** │
                      │ common: сущности, репозитории, модели, фильтр/сортировка    │
                      └──────────────────────────── PostgreSQL ─────────────────────┘
```

Наружу сервис не публикуется: UI ходит через [invest-gateway](../../invest-gateway/docs/architecture.md),
который проксирует только `/api/bonds/**` → `/internal/rest/bonds/**`. Своей авторизации в invest-api нет.

## Модули

| Модуль                            | Что делает                                                                                     |
| --------------------------------- | ---------------------------------------------------------------------------------------------- |
| `app`                             | точка входа `InvestApplication`, конфиг кэшей, Liquibase-миграции, интеграционные тесты       |
| `common`                          | JPA-сущности, репозитории, доменные модели, интерфейсы use case, фильтр и сортировка облигаций |
| `feature/tinkoff-supplier`        | клиент T-Invest API: rate limiter, провайдеры облигаций/купонов/цен/амортизации, расчёт доходностей, синхронизация |
| `feature/bond-supplier`           | шедулеры синхронизации и `BondUseCase` (страницы облигаций для контроллера)                    |
| `feature/currency-service`        | курс валют: сначала ЦБ РФ, если пары нет - budget.org                                          |
| `feature/cb-rf-supplier`          | Feign-клиент ЦБ РФ: курсы, RSS пресс-релизов и событий, ключевая ставка (SOAP DailyInfo)       |
| `feature/news-supplier`           | лента новостей: клиент ISS Мосбиржи, эмитенты выпусков, загрузка и чтение новостей, ставка и курсы для шапки ленты |
| `feature/budget-org-supplier`     | Feign-клиент api.budjet.org                                                                    |
| `feature/telegram-bot`            | Telegram-бот: OkHttp-клиент с прокси из настроек, long polling, команды `/start` `/stop` `/help`, привязка чата к пользователю, проверка связи |
| `feature/ui-service`              | REST-контроллеры, маппинг в DTO, обработка ошибок                                              |
| `invest-api-dto`                  | DTO запросов и ответов, общие с клиентами                                                      |
| `invest-api-spring-boot-starter`  | Feign-клиент `InvestApiBondClient` + `@EnableInvestApiBondClient` для других сервисов и тестов |

Слои внутри модулей: `controller` → `usecase` → `provider` (внешние API) / `repository` (БД); `mapper` (MapStruct)
переводит между API-объектами, моделями `*Model`, сущностями и DTO. Реализации синхронизации помечены `@Active`.

## Синхронизация данных

Ночная цепочка `BondDataSyncScheduler` (cron из `ru.invest.api.stock.supplier.scheduler.sync`, по умолчанию 03:00 МСК).
С `sync.run-on-startup: true` `BondDataSyncStartupRunner` запускает её ещё и сразу после старта - **в фоновом потоке**:
слушатель `ApplicationReadyEvent` выполняется в `main`, и пока он не вернётся, приложение не становится ready
(health отвечает 503, Kubernetes перезапустил бы под по startup-пробе). Прогон идёт через бин шедулера, поэтому
на него тоже действует блокировка ShedLock.

Шаги строго по очереди, каждый включается флагом `sync.steps.<шаг>`; упавший шаг логируется, следующие идут на
имеющихся данных:

1. **bond** - все облигации из `InstrumentsService.Bonds` (статус BASE), дубли по uid и тикеру отбрасываются;
   облигации, которых больше нет в ответе, удаляются.
2. **coupon** - график купонов для облигаций с плавающим купоном, без загруженных купонов или с графиком,
   оборванным раньше погашения, пачками по 100, параллельно в пуле `COUPON_EXECUTOR_SERVICE`. Ошибки изолированы
   по облигации: если API не ответил, у облигации остаётся старый график; если не сохранилась пачка, облигации
   сохраняются по одной, и в лог попадает только та, что не сохранилась.
3. **amortization** - график частичных погашений номинала (события `EVENT_TYPE_MTY`) для облигаций с `amortizationFlag`;
   при ошибке по облигации остаётся старый график.
4. **price** - последние цены (`MarketDataService.GetLastPrices`), затем пересчёт купонной доходности,
   доходности к погашению и к оферте пачками по 500, каждая пачка в своей транзакции.
5. **candle** - дневные свечи цены (`MarketDataService.GetCandles`, только биржевые) для графика. У облигации без
   свечей - вся история окнами по 5 лет назад, пока окно не окажется пустым; иначе - с последней сохранённой свечи
   включительно (она могла быть незавершённой). Облигации по одной, каждая в своей транзакции; ошибка по облигации
   пишется в лог и не останавливает остальные.

## Лента новостей

`NewsSyncScheduler` (модуль `news-supplier`) - раз в 15 минут (`ru.invest.api.news.scheduler.cron`) и в фоне при
старте (`run-on-startup`), своя ShedLock-блокировка `NewsSync`. Шаги независимы, упавший пишется в лог:

1. **эмитенты** - раз в 20 часов: все торгуемые облигации Мосбиржи с эмитентом (`/iss/securities`, ~30 страниц),
   в `bond_issuer` сохраняются только выпуски из системы;
2. **Банк России** - RSS пресс-релизов и «Событий и комментариев»;
3. **Мосбиржа** - новости `/iss/sitenews` новее последней сохранённой (первый раз - 4 страницы, ~3 дня), без
   технических сообщений; у каждой загружается текст, чтобы найти ISIN выпусков;
4. **рейтинги** - списки релизов АКРА и НКР (HTML, разбор jsoup), последние 50;
5. **СМИ** - RSS-ленты из `ru.invest.api.news.feeds` (`RestNewsHttpClient` - RestClient, а не Feign: у каждой ленты
   свой адрес);
6. **очистка** - новости старше 180 дней.

ISIN и эмитенты ищутся в новостях всех источников одним `IssuerMatcher`, построенным по `bond_issuer` в начале прогона.

Правила фильтра и привязки новостей к облигациям - в [business-rules.md](business-rules.md#лента-новостей).
Ключевая ставка и курсы для шапки ленты (`MarketOverviewUseCase`) не хранятся: запрашиваются у ЦБ и кэшируются.

`PriceSyncScheduler` - частое обновление цен в торговые часы (`scheduler.price`, по умолчанию выключен).
Оба шедулера делят ShedLock-блокировку `BOND_DATA_SYNC_LOCK` (таблица `shedlock`, `JdbcTemplateLockProvider`
по часам БД в `SchedulingConfiguration`): пока идёт ночная цепочка, обновление цен пропускается, и при нескольких
экземплярах цепочка идёт только на одном. Блокировка срабатывает только при вызове через Spring-бин - вызов
`sync()` изнутри того же класса её обходит.

Те же шаги можно запустить вручную через maintenance-эндпоинты (см. ниже).

Запросы к T-Invest API идут через `*GrpcRateLimitedWrapper` с лимитами по методам из `invest.rate-limiter`
(серверные лимиты: developer.tbank.ru/invest/intro/intro/limits). Retry SDK повторяет только `RESOURCE_EXHAUSTED`.
`GetCandles` - 5 запросов в секунду (`invest.rate-limiter.candles`): лимит сервиса MarketData 600 в минуту общий с
`GetLastPrices`, но шаги цен и свечей идут по очереди.

## Кэш

Caffeine, менеджеры в `app/.../CacheConfig`:

| Кэш                           | Что лежит                                        | TTL  |
| ----------------------------- | ------------------------------------------------ | ---- |
| `BOND_REPOSITORY_CACHE`       | все облигации из БД с ценой, купоном и ценой в рублях | 24 ч, сбрасывается после каждого шага синхронизации |
| `BOND_TINKOFF_API_CACHE`      | ответ `Bonds` из T-Invest API                    | 24 ч |
| `CB_RF_ALL`, `BUDGET_ORG`, `CURRENCY_PROVIDER` | курсы валют                     | 12 ч |
| `MARKET_OVERVIEW`             | ключевая ставка и курсы для шапки ленты; пустой обзор (ЦБ не ответил) не кэшируется | 30 мин |
| `PRICE`, `COUPON`             | ответы T-Invest по ценам и купонам               | 24 ч |

Список облигаций для UI целиком строится из кэша: фильтрация, сортировка и пагинация идут в памяти
(`BondUseCaseImpl.toPage`), страница - срез отсортированного списка. Карточка облигации (`GET /bonds/{ticker}`)
читается из БД мимо кэша, вместе с графиком купонов.

## База данных

PostgreSQL, схема задаётся в конфиге (`spring.liquibase.default-schema`, `connection-init-sql: SET search_path`).
Миграции - Liquibase, `app/src/main/resources/liquibase/db.changelog-master.xml`. Схему Liquibase не создаёт,
она должна существовать заранее. Гранты пользователю приложения выдаются changeSet'ом с `runAlways`.

| Таблица        | Что                                                                                  |
| -------------- | ------------------------------------------------------------------------------------ |
| `bond`         | облигация: тикер, uid, ISIN, название, сектор, риск, валюта, даты погашения и оферты, флаг амортизации |
| `coupon`       | купон облигации (1:1): число выплат в год, фиксированный ли, купонная доходность, доходность к погашению и к оферте |
| `coupon_data`  | график купонных выплат: дата, размер, валюта                                          |
| `price`        | цена (1:1): номинал, текущая цена, % от номинала, НКД                                 |
| `amortization` | график частичных погашений номинала                                                   |
| `price_candle` | дневные свечи цены в % от номинала, уникальны по `(uid, candle_date)`; удаляются вместе с облигацией (`on delete cascade`) |
| `news`         | внешние новости: источник, id у источника (уникальны вместе), заголовок, анонс, ссылка, время публикации |
| `news_link`    | к чему относится новость: выпуск (`isin`) или эмитент (`emitter_id`); удаляется вместе с новостью |
| `bond_issuer`  | эмитент выпуска по данным Мосбиржи: id эмитента, название, ИНН; с облигацией связан по ISIN, без внешнего ключа |
| `telegram_link` | чат Telegram пользователя: `user_id` (sub Keycloak) - первичный ключ, `chat_id` уникален, @username, время привязки |
| `telegram_link_token` | одноразовые токены ссылки привязки: SHA-256 токена, пользователь, срок (10 минут) |
| `shedlock`     | блокировки шедулеров                                                                  |

У всех таблиц аудит: `created_at/by`, `updated_at/by` (шедулер пишет процесс `SCHEDULER_PROCESS`,
maintenance-эндпоинты - значение заголовка `From`).

## REST API

Контроллеры в `feature/ui-service`. Описание - Swagger: `/swagger-ui.html`, `/api-docs`.

| Метод и путь                                                  | Что                                                       |
| ------------------------------------------------------------- | --------------------------------------------------------- |
| `POST /internal/rest/bonds/{all\|local\|foreign}/page?page=&size=` | страница облигаций; в теле `BondParametersRequest`: сортировки, фильтры по цене, риску, ОФЗ. `size` 1..500, по умолчанию 50 |
| `GET /internal/rest/bonds/{ticker}`                           | облигация с ценой, купоном и графиком выплат              |
| `GET /internal/rest/bonds/search?query=&limit=`               | нестрогий поиск по тикеру, ISIN и названию среди облигаций в кэше; `limit` 1..50, по умолчанию 20 |
| `GET /internal/rest/bonds/{ticker}/candles?interval=&from=&to=` | свечи цены в % от номинала; `interval` DAY / WEEK / MONTH (по умолчанию DAY), даты YYYY-MM-DD, по умолчанию - вся история по сегодня |
| `POST /internal/rest/bonds/{ticker}/calculation`              | доход за период владения; в теле `ProfitCalculationRequest` (даты, количество, цены покупки и продажи) |
| `GET /internal/rest/bonds/{ticker}/events`                    | события облигации из её данных: купоны, амортизация, оферта, погашение - за 3 месяца назад и вперёд |
| `GET /internal/rest/bonds/news?source=&page=&size=`           | лента новостей, новые первыми; `source` - CBR_PRESS, CBR_EVENT, MOEX (несколько через запятую), `size` 1..100, по умолчанию 20 |
| `GET /internal/rest/bonds/{ticker}/news?page=&size=`          | новости облигации: упомянут её ISIN или новость относится к её эмитенту |
| `GET /internal/rest/bonds/market-overview`                    | ключевая ставка (с какого дня и прошлая, изменение с начала года и на последнем заседании) и курсы USD, EUR, CNY с изменением |
| `GET /internal/rest/alerts/telegram`                          | статус Telegram пользователя: включён ли бот, привязан ли чат, отвечает ли Telegram |
| `POST /internal/rest/alerts/telegram/link-token`              | одноразовая ссылка `https://t.me/<bot>?start=<токен>` на 10 минут; прежние ссылки пользователя перестают действовать; бот выключен - `422` |
| `DELETE /internal/rest/alerts/telegram`                       | отвязать чат (`204`, в том числе если чата не было)       |
| `POST /internal/rest/maintenance/bond/sync-all`               | синхронизация облигаций                                   |
| `POST /internal/rest/maintenance/news/sync-all`               | загрузка новостей (в фоне, 202 / 409)                     |
| `POST /internal/rest/maintenance/bond/{coupon\|amortization\|price\|candle}/sync-all` | синхронизация купонов / амортизации / цен / свечей |
| `POST /internal/rest/maintenance/bond/{coupon\|amortization\|price\|candle}/sync-by-ticker/{ticker}` | то же для одной облигации |

Maintenance-эндпоинты требуют заголовок `From` (кто запустил, пишется в аудит; без него - `400`) и через gateway не
доступны. `sync-all` идёт в фоне (`MaintenanceTaskRunner`): ответ сразу `202 Accepted`, ход и итог - в логе; пока
та же задача не закончилась, повторный запуск получает `409 Conflict`. Полная синхронизация свечей - десятки минут,
держать на ней HTTP-запрос нельзя. С ночной цепочкой ручной запуск не согласован (ShedLock его не видит) - не
запускайте вручную шаг, который сейчас идёт по расписанию или при старте. `sync-by-ticker` - синхронно.
`local` - облигации в рублях, `foreign` - в иностранной валюте (правило - в [business-rules.md](business-rules.md)).

Эндпоинты `/alerts/**` - пользовательские: пользователя (sub Keycloak) передаёт invest-gateway в заголовке `X-User-Id`,
без заголовка - `401`. Своей проверки подлинности у invest-api нет: доверие держится на том, что invest-api доступен
только через gateway, а gateway затирает заголовок из браузера.

Для других Java-сервисов есть стартер: `@EnableInvestApiBondClient` подключает Feign-клиент `InvestApiBondClient`
с базовым путём `/internal/rest`. Feign не отправляет `null` в теле, поэтому для запроса без фильтров передаётся
пустой `BondParametersRequest`.

## Внешние зависимости

| Система             | Зачем                                   | Настройка                                           |
| ------------------- | --------------------------------------- | --------------------------------------------------- |
| T-Invest API (gRPC) | облигации, купоны, события, цены, свечи | `invest.connector.token`, `invest.rate-limiter.*`   |
| ЦБ РФ               | курсы к рублю, RSS новостей, ключевая ставка | `spring.cloud.openfeign.client.config.cb-rf-client.url` |
| Мосбиржа ISS        | новости биржи, эмитенты облигаций; бесплатно, без ключа | `...moex-iss-client.url`              |
| АКРА, НКР           | рейтинговые действия (HTML-списки)      | `ru.invest.api.news.acra-url`, `...nkr-url`         |
| СМИ (RSS)           | Интерфакс, РБК, Коммерсантъ, Ведомости, Финам, Frank Media, Smart-Lab | `ru.invest.api.news.feeds` |
| api.budjet.org      | курсы пар без рубля и запасной источник | `...budget-org-client.url`                          |
| Telegram Bot API    | бот уведомлений: long polling, ответы на команды | `ru.invest.api.telegram.*`; в России заблокирован - через TUN VPN или прокси ([proxy.md](features/INVEST-001-telegram-bot/proxy.md)) |

## Telegram-бот

Модуль `feature/telegram-bot`, включается `ru.invest.api.telegram.enabled`. Один `OkHttpClient` (прокси из
`ru.invest.api.telegram.proxy`, `NONE` - напрямую) на long polling и отправку: остальные клиенты приложения прокси не
видят. `TelegramBotRunner` (`SmartLifecycle`) запускает long polling после старта контекста; пока Telegram недоступен,
библиотека `telegrambots` сама повторяет `getUpdates`, приложение работает. Long polling - в одном экземпляре
приложения. `TelegramAvailabilityChecker` раз в 5 минут вызывает `getMe`; результат и итог каждой отправки
(`TelegramMessageSender`) хранятся в памяти (`TelegramAvailability`) и отдаются в статусе для UI.

Привязка: UI получает одноразовую ссылку `t.me/<bot>?start=<токен>`, пользователь нажимает «Старт», бот получает
`/start <токен>` и сохраняет `chat_id` за владельцем токена (`TelegramLinkUseCase`). Правила - в
[business-rules.md](business-rules.md#telegram-привязка-чата). Обрабатываются только личные чаты.

Сертификаты invest-public-api.tinkoff.ru и acra-ratings.ru подписаны корневым сертификатом Минцифры - его нужно добавить в truststore JVM
(в образе это делает Dockerfile, см. [deployment.md](deployment.md)).
