# INVEST-001: портфель - устройство

```
UI /portfolio ──▶ invest-gateway /api/portfolio/** ──X-User-Id──▶ invest-api /internal/rest/portfolio/**
                                                                    │
                                              PortfolioController (ui-service)
                                                                    │
                          PortfolioUseCase (portfolio-service) ──▶ portfolio_position
                                                                    │
                                   BondUseCase.getByTickers ──▶ bond + price (одним запросом)
```

## Модули

- `feature/portfolio-service` - новый модуль: `PortfolioUseCase` (CRUD, расчёт стоимости и дохода, итоги), модели
  `PositionModel`, `PortfolioModel`, `PortfolioTotalModel`, `PositionRequestModel`. Зависит от `bond-supplier`.
- `common` - сущность `PortfolioPosition`, `PortfolioPositionRepository`; в `BondRepository` -
  `findWithPriceByTickerIn` (облигации с ценой и купоном по списку тикеров); код ошибки `PORTFOLIO_INVALID`.
- `tinkoff-supplier` / `bond-supplier` - `TinkoffBondUseCase.findByTickers`, `BondUseCase.getByTickers`: облигации
  портфеля читаются одним запросом из БД, мимо кэша, без рублёвой цены (`currentInRub` портфелю не нужна).
- `ui-service` - `PortfolioController`, `PortfolioDtoMapper`.
- `invest-api-dto` - пакет `ru.invest.api.dto.portfolio`.

## БД

Таблица `portfolio_position` (миграция `tables/012-portfolio.xml`): `user_id` (sub Keycloak), `ticker` без внешнего
ключа (облигации пересоздаются синхронизацией), `quantity`, `buy_price` (% от номинала, `numeric(10,4)`),
`buy_date`, `created_at`, `updated_at`. Индекс по `user_id`. Уникальности по тикеру нет - покупок одной облигации
может быть несколько.

## API

| Метод и путь                                     | Что                                                                |
| ------------------------------------------------ | ------------------------------------------------------------------ |
| `GET /internal/rest/portfolio`                   | `{positions, totals}`: покупки по тикеру и дате, итоги по валютам |
| `POST /internal/rest/portfolio/positions`        | `{ticker, quantity, buyPrice, buyDate?}` → `201`                   |
| `PUT /internal/rest/portfolio/positions/{id}`    | `{quantity, buyPrice, buyDate?}`; тикер из тела не используется   |
| `DELETE /internal/rest/portfolio/positions/{id}` | `204`                                                              |

Ошибки: без `X-User-Id` - `401`; чужая или несуществующая покупка, неизвестная облигация - `404`; `quantity` вне
1..1 000 000, `buyPrice` вне 0,01..300 - `400` (bean validation); нет тикера, дата покупки в будущем, больше 200
покупок - `422`. Дату в будущем проверяет use case, а не `@PastOrPresent`: «сегодня» - по часовому поясу сервиса
(`DateTimeMapper.getNow`).

## UI (invest-api-ui)

`src/pages/PortfolioPage.tsx`, запросы - `src/api/portfolio.ts`, хуки `usePortfolio` / `usePortfolioMutations`.
Карточки итогов по валютам, таблица покупок (прибыль зелёная, убыток красный), окно добавления и изменения.
При выборе облигации цена покупки подставляется из её текущей цены, пока пользователь не ввёл свою.

## Решения и отвергнутые варианты

- **Доход без купонов.** Нужен доход от цены покупки; купоны потребовали бы графика выплат по каждой покупке.
  Полный доход за период считает калькулятор.
- **Номинал - текущий для обеих сумм.** У облигаций с амортизацией номинал на дату покупки был больше, но тогда
  доход включал бы погашенный номинал как убыток. Процент дохода от номинала не зависит:
  `(текущая цена − цена покупки) / цена покупки`.
- **Отдельный модуль, а не в `alert-service`.** С уведомлениями у портфеля общий только пользователь.
- **Чтение мимо кэша облигаций.** Кэш держит всю выборку и сбрасывается после синхронизаций; портфелю нужны единицы
  тикеров - запрос `in (...)` с `join fetch` цены дешевле и всегда свежий.
