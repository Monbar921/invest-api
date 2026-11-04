# Тестирование invest-api

## Как запускать

```bash
mvn verify                       # checkstyle + все тесты + сборка
mvn test -pl app -am             # только тесты
mvn test -pl app -am -Dtest=YieldToMaturityCalculationTest
```

Нужен Docker: интеграционные тесты поднимают PostgreSQL через Testcontainers. На Windows - запущенный Docker Desktop.

## Устройство

Тесты лежат в модуле `app`. Тесты шедулеров - обычные unit-тесты на моках, остальные - интеграционные (`app/src/test/java/ru/invest/api`) и наследуются от `AbstractInvestApplicationTest`:

- поднимается всё приложение (`@SpringBootTest`, порт 8099 из `app/src/test/resources/application.yml`);
- PostgreSQL 17 в Testcontainers, контейнер переиспользуется между запусками (`withReuse(true)`), миграции Liquibase
  применяются как в проде;
- внешние системы замоканы `@MockitoBean`: `InstrumentsGrpcRateLimitedWrapper`, `MarketDataGrpcRateLimitedWrapper`
  (T-Invest), `CbRfClient`, `BudgetOrgClient`;
- REST вызывается через тот же Feign-клиент, что получают потребители (`@EnableInvestApiBondClient`
  в `InvestApiTestConfiguration`) - заодно проверяется стартер;
- шедулеры в тестах выключены (`scheduler.*.enabled: false`), шаги синхронизации вызываются напрямую.

| Тест                               | Что проверяет                                                         |
| ---------------------------------- | --------------------------------------------------------------------- |
| `common/BondSyncTest`              | загрузка облигаций, дата оферты, НКД, флаг амортизации                |
| `common/CouponSyncTest`            | загрузка графика купонов, явный период запроса, перезагрузка графика, оборванного раньше погашения, купоны без даты фиксации, изоляция ошибок API и БД по облигации |
| `common/AmortizationSyncTest`      | загрузка графика амортизации, замена графика при повторной синхронизации, пропуск облигаций без амортизации |
| `common/PriceSyncTest`             | цены, НКД, пересчёт купонной доходности, нулевая цена сохраняется как отсутствие цены |
| `common/CandleSyncTest`            | свечи: вся история при первой синхронизации, дальше - с последней свечи, ошибка по облигации не останавливает остальные |
| `common/CandleAggregationTest`     | сборка недели и месяца из дневных свечей                               |
| `common/CouponInterestCalculationTest` | купонная доходность, приведение к году, когда до погашения осталось меньше купонов, чем выплат в год |
| `common/YieldToMaturityCalculationTest` | расчёт доходности к погашению и к оферте, случаи `null`          |
| `feature/bond/supplier/BondDataSyncSchedulerTest` | порядок шагов цепочки, флаги шагов, продолжение после ошибки |
| `feature/bond/supplier/BondDataSyncStartupRunnerTest` | прогон при старте не блокирует поток старта |
| `feature/ui/service/MaintenanceControllerTest` | `sync-all` в фоне: сразу 202, повторный запуск во время работы - 409, после - снова 202 |
| `feature/ui/service/BondControllerTest` | страницы, фильтры, сортировки, пагинация, карточка облигации, поиск, калькулятор, свечи, 404 и 405 на неизвестный путь и метод, 400 на неверную дату |

## Тестовые данные

| Где                                    | Что                                                                  |
| -------------------------------------- | -------------------------------------------------------------------- |
| `app/src/test/resources/fixtures/*.pbtxt` | ответы T-Invest API в protobuf text format (`bonds-response`, `coupon-response`), читаются `loadFixture()` / `loadCouponFixture()` |
| `app/src/test/resources/sql/*.sql`     | наполнение БД для тестов контроллера и расчётов, `clean-up.sql` - очистка между тестами |

Новые колонки в БД нужно добавлять и в `clean-up.sql`/sql-наборы, иначе тесты будут видеть данные соседних.

## Соглашения

- Время в расчётах берётся через `DateTimeMapper.getNow()`, чтобы тест мог зафиксировать «сегодня».
- Для каждого случая, когда расчёт возвращает `null` (см. [business-rules.md](business-rules.md)), должен быть тест.
- Checkstyle (`configuration/checkstyle/checkstyle.xml`) запускается в `validate` и валит сборку;
  pre-commit hook ставится `git-build-hook-maven-plugin`.
