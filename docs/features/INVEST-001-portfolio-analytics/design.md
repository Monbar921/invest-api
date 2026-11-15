# INVEST-001: аналитика портфеля - устройство

```
UI /portfolio?tab=analytics ──▶ gateway /api/portfolio/analytics ──X-User-Id──▶ invest-api /internal/rest/portfolio/analytics
                                                                                   │
                                       PortfolioAnalyticsUseCase (portfolio-service)
                                         ├─ PortfolioUseCase.getPortfolio   открытые строки и их стоимость в рублях
                                         ├─ BondUseCase.getByTicker         сектор, тип купона, погашение, доходность,
                                         │                                   дюрация, рейтинг (данные скринера)
                                         ├─ BondIssuerRepository + IssuerTitles  эмитент по ISIN, короткое название
                                         └─ PortfolioAnalyticsCalculator    доли, средние, чувствительность
```

## Модули

- `feature/portfolio-service`:
  - `PortfolioAnalyticsUseCase` / `PortfolioAnalyticsUseCaseImpl` - собирает позиции: строки портфеля
    (`PortfolioGroupModel`) с данными облигации и эмитента → `AnalyticsPositionModel`; причина отсутствия оценки -
    `UnvaluedReason`;
  - `PortfolioAnalyticsCalculator` - расчёт без Spring и БД (доли, «нет данных», средние, концентрация,
    чувствительность), покрыт отдельным unit-тестом;
  - модели `PortfolioAnalyticsModel`, `AnalyticsShareModel`, `WeightedValueModel`, `RateSensitivityModel`,
    `UnvaluedPositionModel`;
  - новая зависимость - `news-supplier` (эмитенты и `IssuerTitles.shortTitle`); цикла нет: news-supplier зависит
    только от tinkoff-supplier.
- `ui-service` - `GET /internal/rest/portfolio/analytics` в `PortfolioController`, маппинг `PortfolioDtoMapper.toDto`.
- `invest-api-dto` - `PortfolioAnalyticsDto`, `PortfolioAnalyticsShareDto`, `PortfolioAnalyticsWeightedDto`,
  `PortfolioRateSensitivityDto`, `PortfolioUnvaluedPositionDto` (причина - строкой).
- БД не меняется.

## API

`GET /internal/rest/portfolio/analytics` (`X-User-Id`, без него - `401`). Ответ:

| Поле | Что |
| --- | --- |
| `valueInRub` | стоимость оценённых позиций; `null` - оценённых нет |
| `bonds` | облигаций в открытых позициях, с неоценёнными |
| `unvalued[]` | `{ticker, bondName, reason}`: `UNKNOWN_BOND`, `NO_PRICE`, `NO_RATE` |
| `issuers[]`, `sectors[]`, `ratings[]`, `currencies[]`, `couponTypes[]`, `maturities[]` | `{key, title, valueInRub, share, tickers}`; `key = null` - «нет данных», последней строкой |
| `yieldToMaturity`, `durationMacaulay`, `durationModified` | `{value, coveredShare}` |
| `largestIssuer` | строка из `issuers` или `null` |
| `rateSensitivity` | `{shiftPercent, valueChangeUp, valueChangeDown, changePercent, coveredShare}` или `null` |

## Решения

- **Позиции берутся из `getPortfolio`**, а не считаются заново: остаток после продаж (FIFO) и погашений, рублёвая
  стоимость и её отсутствие - ровно те, что пользователь видит во вкладке «Покупки». Цена - лишний расчёт полного
  дохода в том же запросе; для портфеля частного инвестора (десятки облигаций) это не заметно.
- **Данные облигации - из карточки (`getByTicker`)**, а не из кэша списка: у кэша нет выборки по тикерам, а карточка
  уже добавляет рейтинг через те же обогатители скринера. По запросу на облигацию - так же, как `getPortfolio`
  грузит графики.
- **Вес - стоимость без НКД.** НКД не говорит о структуре портфеля и колеблется между выплатами; так же стоимость
  считается в портфеле.
- **Неоценённые позиции - отдельным списком**, а не с весом по цене покупки: подставленная цена исказила бы доли,
  а принцип проекта - не отдавать приблизительное значение.
- **Доли методом наибольшего остатка** - иначе 3 равные позиции дают 99,99 %, а критерий этапа - «в сумме 100 %».
- **Флоатеры не входят в чувствительность к ставке.** Их дюрация по известным купонам не описывает реакцию цены на
  ставку (купон пересчитывается). Учтённая доля - в `coveredShare`, в UI - оговорка.
- **Лестница - по дате погашения.** Оферта - право, а не обязательство погасить; амортизация уже видна в календаре
  выплат.
- **Рейтинг - ступень общей шкалы** (как фильтр скринера), а не буквенная группа: ступеней немного, а группировка
  «AA» вместе с «AA-» скрыла бы разницу, которую пользователь видит в карточке.

Отвергнуто: считать аналитику в UI из `GET /portfolio` - там нет рейтинга, эмитента и дюрации, а правила долей и
«нет данных» пришлось бы повторять на клиенте.

## Тесты

- `PortfolioAnalyticsCalculatorTest` - расчёт на известных примерах (без Spring и БД).
- `PortfolioAnalyticsTest` - REST с Testcontainers: эмитенты, рейтинги, доходность и дюрация вставляются SQL.
- UI: `pages/portfolioAnalytics.test.tsx`.
