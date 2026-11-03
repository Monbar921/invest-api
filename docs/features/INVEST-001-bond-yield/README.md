# INVEST-001: доходность к погашению и к оферте

**Статус:** в работе. Ветка `feature/INVEST-001`, PR в `master` ещё не открыт.

## Цель

Купонная доходность (сумма купонов за год к цене) плохо сравнивает облигации: не учитывает НКД, цену покупки
относительно номинала, амортизацию и время до погашения. Нужна эффективная доходность к погашению и к оферте,
чтобы по ней фильтровать и сортировать список.

## Что входит

- Новые данные из T-Invest API: НКД, дата оферты (`callDate`), флаг амортизации, график амортизации (события `MTY`).
- Новый шаг ночной синхронизации `amortization` и maintenance-эндпоинты
  `/internal/rest/maintenance/bond/amortization/{sync-all, sync-by-ticker/{ticker}}`.
- Расчёт `yieldToMaturity` и `yieldToOffer` при каждой синхронизации цен.
- Новые поля в API: `BondDto.offerDate`, `amortizationFlag`; `CouponDto.yieldToMaturity`, `yieldToOffer`;
  `PriceDto.aci`.
- Сортировка по `YIELD_TO_MATURITY` и `YIELD_TO_OFFER`.
- Флаг `sync.run-on-startup`: прогон цепочки в фоне сразу после старта, чтобы новые поля заполнились без ожидания
  ночного запуска.

Правила расчёта - в [business-rules.md](../../business-rules.md#доходность-к-погашению-и-к-оферте),
как сделано - в [design.md](design.md).

## Что осталось

- Колонки доходностей и сортировка в [invest-api-ui](../../../../invest-api-ui/) - не сделано.
- PR в master.
