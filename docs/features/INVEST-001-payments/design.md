# INVEST-001: Оплата - как сделано

## Модули

| Где | Что |
| --- | --- |
| `common` | сущности `Payment`, `AccountAutoRenewal`, репозитории (`PaymentRepository.findByIdForUpdate` - блокировка строки), `PaymentStatus`, `PaymentKind`, `SubscriptionSource.PAYMENT`, `AccountSubscription.paymentId` |
| `feature/account-service` | у тарифа `price` и `period` (`PlanProperties`, проверка при старте), `PlanModel.purchasable()`, `PeriodTexts` («1 месяц»); `AccountPlanUseCase`: `findPlan`, `current`, `grantPaid` (продление от последнего дня), `revokePaid` (возврат); ручные `grant` / `revoke` отключают автопродление |
| `feature/payment-service` (новый) | `PaymentProperties` (`ru.invest.api.payments`), интерфейс `PaymentProvider`, Т-Касса (`TbankPaymentProvider`, `TbankClient`, `TbankSignature`, `TbankStatuses`), `PaymentApplier` (применение состояния), `PaymentUseCaseImpl`, `RenewalUseCaseImpl`, `RenewalScheduler` |
| `feature/ui-service` | `AccountController` (покупка, платежи, отмена автопродления, оплата в `/account/plan`), `PaymentNotificationController` (webhook), `PaymentMaintenanceController`, `AccountDtoMapper`, `PaymentProviderException` → `502` |
| `invest-api-dto` | `PaymentDto`, `PaymentsInfoDto`, `CreatePaymentRequest`; у `PlanDto` - `price`, `period`, `periodTitle`, `purchasable`; у `AccountPlanDto` - `payments` |
| БД | `017-payment.xml`: таблицы `payment`, `account_auto_renewal` (уникальный частичный индекс - одно действующее на пользователя), колонка `account_subscription.payment_id` |

## Покупка

```
UI ──POST /account/payments {plan}──▶ PaymentUseCase.purchase
        payment NEW (order_id = UUID) ──commit──▶ Т-Касса Init (Recurrent=Y, CustomerKey=sub,
        SuccessURL/FailURL = /plan?payment=<id>, NotificationURL) ──▶ payment PENDING + PaymentURL
UI ◀── confirmationUrl ── браузер уходит на страницу оплаты Т-Банка
Т-Касса ──webhook──▶ gateway /api/payments/notifications/tbank ──▶ PaymentUseCase.handleNotification
        подпись Token ✓ → платёж по order_id → PaymentApplier.apply → grantPaid → автопродление (RebillId)
UI ◀── возврат на /plan?payment=<id> ── опрос GET /account/payments/<id> раз в 3 с
```

Платёж сохраняется до запроса к провайдеру: webhook находит его по номеру заказа. HTTP к провайдеру - вне транзакции
БД. Провайдер не ответил на Init - платёж `FAILED`, пользователю `502` «Платёжный сервис не ответил».

`GET /account/payments/{id}` у платежа `PENDING` сначала запрашивает статус у провайдера (`GetState`) и применяет его
тем же `PaymentApplier`. Так оплата доходит и без webhook - локально, где Т-Касса не достучится до gateway. `GetState`
не возвращает `RebillId`, поэтому автопродление включится, только когда придёт webhook (он применится к уже
оплаченному платежу и сохранит карту, не продлевая срок).

## Идемпотентность

`PaymentApplier.apply` работает под `select ... for update` строки платежа - webhook и опрос статуса не применят
платёж одновременно. Переходы:

| Сейчас | Пришло | Что происходит |
| ------ | ------ | -------------- |
| `NEW` / `PENDING` | `SUCCEEDED` | `grantPaid`, `subscription_id`, `paid_at`; покупка с `RebillId` - автопродление |
| `NEW` / `PENDING` | `FAILED` | `FAILED`, сообщение провайдера; у автопродления - неудачная попытка |
| `NEW` / `PENDING` | промежуточный | `PENDING` |
| `SUCCEEDED` | `SUCCEEDED` | тариф не трогается; пришёл `RebillId`, а автопродления от этого платежа ещё нет - включается |
| `SUCCEEDED` | `REFUNDED` | `REFUNDED`, `revokePaid`, автопродление отключается |
| `FAILED` / `REFUNDED` | любой | ничего |

Сумма провайдера не совпала с платежом или чужой `PaymentId` - ничего не меняется, ошибка в логе. Неизвестный
`OrderId` подтверждается (`OK`) без изменений - иначе провайдер повторял бы уведомление.

## Т-Касса

- Подпись `Token`: параметры верхнего уровня без вложенных объектов и самого `Token`, плюс `Password`, по алфавиту
  ключей, значения склеены, SHA-256 в hex. Так подписываются запросы и так проверяется webhook (сравнение за
  постоянное время). Тест сверяет с примером из документации.
- Webhook: проверяется `TerminalKey` и `Token`, ответ - `OK` (`text/plain`), неверная подпись - `400`.
- Статусы: `CONFIRMED` - оплачен; `REJECTED`, `AUTH_FAIL`, `CANCELED`, `DEADLINE_EXPIRED`, `REVERSED`,
  `PARTIAL_REVERSED` - не прошёл; `REFUNDED` - возврат; остальные (`NEW`, `FORM_SHOWED`, `AUTHORIZED`,
  `PARTIAL_REFUNDED`...) - ждём. `Success=false` в webhook - не прошёл.
- Автопродление: `Init` без страницы оплаты, затем `Charge` с `PaymentId` и `RebillId`. Отказ `Charge`
  (`Success=false`) - неудачная попытка; нет ответа - платёж `PENDING`, его итог узнаётся при следующем запуске
  (`GetState`), новое списание до этого не создаётся.
- Суммы у Т-Кассы - в копейках, у нас - рубли (`numeric(12,2)`).

## Автопродление

`RenewalScheduler` (раз в `renewal.interval`, ShedLock) → `RenewalUseCase.renewDue`: действующие автопродления с
`next_charge_at <= now`, каждое отдельно (ошибка одного не останавливает другие).

1. Есть списание этого автопродления без итога - узнать его статус у провайдера, нового не создавать.
2. Срок списания прошёл больше чем на `retry-interval × max-attempts` (шедулер не работал) - отключить (`EXPIRED`):
   задним числом не списываем.
3. Тариф больше не продаётся или убран - отключить (`PLAN_NOT_SOLD`); у пользователя действует другой тариф -
   отключить (`REPLACED`).
4. Платёж `RENEWAL` и `Charge`. Успех - срок продлевается от последнего дня, `next_charge_at` = новый конец −
   `charge-before`, счётчик неудач - 0. Неудача - `failed_attempts + 1`, повтор через `retry-interval`; на
   `max-attempts` - отключить (`PAYMENT_FAILED`). Тариф при неудаче не меняется и заканчивается в срок.

`next_charge_at` сдвигается при каждой оплате: покупка того же тарифа без новой карты переносит списание на новый
конец срока.

## Решения и отвергнутые варианты

- **Т-Касса как первый провайдер.** Решение 0.2 не принято; роадмап требует проверку подписи webhook - у Т-Кассы она
  есть (`Token`), у ЮKassa webhook не подписан (проверка по IP и повторному запросу статуса). Тестовый терминал
  (`...DEMO`) работает с боевым адресом API. Другой провайдер - ещё одна реализация `PaymentProvider`.
- **Цена у тарифа, а не в настройке оплаты.** Тариф и его цена меняются вместе (решение 0.4), страница «Тариф»
  показывает их одной таблицей. Цена без срока, у бесплатного тарифа или ноль - приложение не стартует.
- **Последний день = база + срок.** Покупка 13 ноября на месяц - по 13 декабря включительно; продление того же
  тарифа - от последнего дня (13 января). Смена тарифа - новый срок с сегодняшнего дня, остаток старого не
  пересчитывается (UI предупреждает).
- **Одна подписка у пользователя** (как в этапе 6): оплата отзывает действующую и создаёт новую со сдвинутым
  сроком. Поэтому возврат старого платежа ничего не отзывает - подписка уже заменена; при таком возврате тариф
  поправляют вручную.
- **Статус в UI - опросом, не push.** После возврата с оплаты UI опрашивает платёж ~2 минуты; бэк при опросе уточняет
  статус у провайдера - работает и без webhook.
- **Webhook без лимита анонимных запросов.** `429` провайдер повторял бы, задерживая включение тарифа; защищает
  подпись.
- **Ручная выдача отключает автопродление** - иначе оно продлило бы тариф, который заменён вручную.

## Тесты

- `PaymentTest` (интеграционный, мок `TbankClient`, webhook - настоящий HTTP с подписью): цены и ссылки в
  `/account/plan`; Init (сумма в копейках, `Recurrent`, `CustomerKey`, адреса возврата, подпись); webhook включает
  тариф один раз и сохраняет карту; неверная и подменённая подпись, не JSON - `400`; неизвестный провайдер - `404`,
  неизвестный заказ - `OK`; отказ не меняет тариф, успех после отказа игнорируется; несовпадение суммы; опрос
  статуса у провайдера и карта из позднего webhook; продление от последнего дня; `422` / `400`; провайдер не ответил
  - `502` и `FAILED` в истории; чужой платёж - `404`; автопродление: списание по карте, отмена, три неудачи
  отключают, списание без ответа проверяется перед новым; ручной отзыв отключает автопродление; возврат отзывает
  тариф; maintenance.
- `TbankSignatureTest`: пример подписи из документации, вложенные объекты не подписываются, проверка webhook, статусы.
- `PaymentPropertiesTest`: выключенная оплата стартует без настроек; включённая без адресов или ключей и неизвестный
  провайдер - не стартует; срок по-русски.
- `PlanPropertiesTest`: цена у платного тарифа; цена у бесплатного, без срока, срок без цены, ноль - не стартует.
- UI: `plan.test.tsx` - цена в сравнении, покупка только с согласием, переход на оплату, продление и отключение
  автопродления, история, возврат с оплаты (ожидание → успех, отказ).
- gateway: `SecurityConfigurationTest` - webhook без сессии и CSRF уходит в invest-api, остальное под входом;
  `AnonymousRateLimitTest` - webhook не ограничивается.
