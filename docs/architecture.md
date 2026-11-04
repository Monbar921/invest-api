# Архитектура invest-api

Сервис собирает облигации, купоны, амортизацию и цены из T-Invest API, хранит их в PostgreSQL, считает доходности
и отдаёт список облигаций с фильтрами и сортировкой. Spring Boot 4, Java 25, Maven multi-module.

```
                      ┌──────────────────────── invest-api ────────────────────────┐
T-Invest API (gRPC) ◀─┤ tinkoff-supplier ◀── bond-supplier (шедулеры, BondUseCase)   │
ЦБ РФ (XML)         ◀─┤ cb-rf-supplier   ◀┐                                         │
api.budjet.org      ◀─┤ budget-org-supp. ◀┴─ currency-service (курсы валют)         │
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
| `feature/cb-rf-supplier`          | Feign-клиент курсов ЦБ РФ                                                                      |
| `feature/budget-org-supplier`     | Feign-клиент api.budjet.org                                                                    |
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
| `POST /internal/rest/maintenance/bond/sync-all`               | синхронизация облигаций                                   |
| `POST /internal/rest/maintenance/bond/{coupon\|amortization\|price\|candle}/sync-all` | синхронизация купонов / амортизации / цен / свечей |
| `POST /internal/rest/maintenance/bond/{coupon\|amortization\|price\|candle}/sync-by-ticker/{ticker}` | то же для одной облигации |

Maintenance-эндпоинты требуют заголовок `From` (кто запустил, пишется в аудит; без него - `400`) и через gateway не
доступны. `sync-all` идёт в фоне (`MaintenanceTaskRunner`): ответ сразу `202 Accepted`, ход и итог - в логе; пока
та же задача не закончилась, повторный запуск получает `409 Conflict`. Полная синхронизация свечей - десятки минут,
держать на ней HTTP-запрос нельзя. С ночной цепочкой ручной запуск не согласован (ShedLock его не видит) - не
запускайте вручную шаг, который сейчас идёт по расписанию или при старте. `sync-by-ticker` - синхронно.
`local` - облигации в рублях, `foreign` - в иностранной валюте (правило - в [business-rules.md](business-rules.md)).

Для других Java-сервисов есть стартер: `@EnableInvestApiBondClient` подключает Feign-клиент `InvestApiBondClient`
с базовым путём `/internal/rest`. Feign не отправляет `null` в теле, поэтому для запроса без фильтров передаётся
пустой `BondParametersRequest`.

## Внешние зависимости

| Система             | Зачем                                   | Настройка                                           |
| ------------------- | --------------------------------------- | --------------------------------------------------- |
| T-Invest API (gRPC) | облигации, купоны, события, цены, свечи | `invest.connector.token`, `invest.rate-limiter.*`   |
| ЦБ РФ               | курсы к рублю                           | `spring.cloud.openfeign.client.config.cb-rf-client.url` |
| api.budjet.org      | курсы пар без рубля и запасной источник | `...budget-org-client.url`                          |

Сертификат invest-public-api.tinkoff.ru подписан корневым сертификатом Минцифры - его нужно добавить в truststore JVM
(в образе это делает Dockerfile, см. [deployment.md](deployment.md)).
