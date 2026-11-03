# Тестирование invest-api

## Как запускать

```bash
mvn verify                       # checkstyle + все тесты + сборка
mvn test -pl app -am             # только тесты
mvn test -pl app -am -Dtest=YieldToMaturityCalculationTest
```

Нужен Docker: интеграционные тесты поднимают PostgreSQL через Testcontainers. На Windows - запущенный Docker Desktop.

## Устройство

Все тесты - интеграционные, в модуле `app` (`app/src/test/java/ru/invest/api`), наследуются от
`AbstractInvestApplicationTest`:

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
| `common/CouponSyncTest`            | загрузка графика купонов                                              |
| `common/AmortizationSyncTest`      | загрузка графика амортизации, замена графика при повторной синхронизации, пропуск облигаций без амортизации |
| `common/PriceSyncTest`             | цены, НКД, пересчёт купонной доходности                               |
| `common/YieldToMaturityCalculationTest` | расчёт доходности к погашению и к оферте, случаи `null`          |
| `feature/bond/supplier/BondDataSyncSchedulerTest` | порядок шагов цепочки, флаги шагов, продолжение после ошибки |
| `feature/ui/service/BondControllerTest` | страницы, фильтры, сортировки, пагинация, карточка облигации     |

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
