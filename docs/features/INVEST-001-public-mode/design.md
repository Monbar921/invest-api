# Публичный режим: устройство

## invest-gateway

### Правила доступа (`SecurityConfiguration`)

| Запрос                                                     | Без сессии                         |
| ---------------------------------------------------------- | ---------------------------------- |
| `/actuator/health`, `GET /api/session`                     | открыт                             |
| `GET`/`HEAD /api/bonds/**`                                 | открыт (если `public-access.enabled`) |
| `POST /api/bonds/*/page`, `POST /api/bonds/*/calculation`  | открыт (если `public-access.enabled`), нужен CSRF-токен |
| остальное под `/api/**` (alerts, portfolio, другие POST)   | `401`                              |
| прочие страницы                                            | редирект на логин                  |

POST у облигаций - это чтение с фильтрами в теле. Открыты поимённо, а не весь `POST /api/bonds/**`: изменяющий
эндпоинт, если он когда-нибудь появится под `/bonds`, не станет публичным незаметно.

CSRF не менялся (`CsrfConfigurer::spa`): cookie `XSRF-TOKEN` ставится на любой ответ, в том числе без сессии, поэтому
анонимный POST без заголовка `X-XSRF-TOKEN` по-прежнему `403`. UI получает cookie первым же запросом `/api/session`.

Error dispatch (`DispatcherType.ERROR`) открыт. Найдено на стенде: `403` от CSRF Tomcat отдаёт через `sendError` →
`/error`, а этот внутренний запрос тоже проходит авторизацию; у гостя он получал `401`, и UI уводил на логин вместо
ошибки. В MockMvc error dispatch не происходит, поэтому тест `errorDispatchOfGuestKeepsOriginalStatus` делает его вручную.

### Состояние входа: `GET /api/session`

`SessionController` отвечает `{"authenticated": true, "username": "ivan"}` или `{"authenticated": false,
"username": null}`, всегда `200`.

Раньше UI проверял сессию запросом `/api/bonds/search` и ждал `401`. Теперь облигации открыты и `401` больше не
означает «гость». Отвергнуто: спрашивать `/api/alerts` и ловить `401` - лишняя ошибка в консоли у каждого гостя и
лишний поход в invest-api.

### Возврат после входа

`LoginReturnFilter` стоит в цепочке Spring Security перед `OAuth2AuthorizationRequestRedirectFilter`: из
`/oauth2/authorization/keycloak?returnTo=/bonds/RU...` кладёт путь в сессию, `LoggingAuthenticationSuccessHandler`
после входа ведёт туда (и удаляет атрибут). Без `returnTo` - как раньше (сохранённый запрос или `/`).

Принимается только путь своего origin: начинается с одного `/`, не `//host` и не `/\host` (браузер считает оба
другим хостом), без управляющих символов и `\`, не длиннее 2048. Иначе параметр игнорируется - защита от open
redirect. Отвергнуто: `setTargetUrlParameter` у стандартного handler'а - он читает параметр из callback Keycloak,
а не из запроса, начавшего вход.

### Ограничение частоты (`AnonymousRateLimitFilter`)

- Только анонимные запросы к `/api/**`; вошедшие не ограничиваются. Фильтр стоит после Spring Security (знает
  пользователя) и после `UserIdHeaderFilter`.
- Token bucket на IP: запас `requests-per-minute` (по умолчанию 120) и пополнение с той же скоростью. Открытие
  карточки - около 6 запросов сразу, поиск по мере ввода - по запросу на паузу; выкачивание всех облигаций - тысячи.
- Сверх лимита - `429`, `Retry-After` в секундах и ProblemDetail с `detail` по-русски (UI показывает его как ошибку).
- Память ограничена `max-clients` (10 000): при переполнении удаляются клиенты с полным запасом, если не помогло -
  всё сбрасывается (лучше пропустить лишнее, чем упасть).
- IP - `getRemoteAddr()`, за прокси его подставляет `ForwardedHeaderFilter` из `X-Forwarded-For`. Прокси перед
  gateway (ingress) должен выставлять этот заголовок сам, а не передавать присланный клиентом - иначе лимит
  обходится подменой заголовка.
- Состояние в памяти экземпляра: при нескольких репликах лимит на каждую. Отвергнуто сейчас: Bucket4j/Redis - одна
  реплика, лишняя зависимость; пересмотреть при масштабировании.

### Настройки

```yaml
invest.gateway.public-access:
  enabled: true                 # false - облигации снова только с сессией
  rate-limit:
    requests-per-minute: 120    # 0 - без ограничения
    max-clients: 10000
```

## invest-api-ui

- `index.html` до загрузки бандла запрашивает `/api/session` (`window.investSession`), `main.tsx` ждёт ответ и
  кладёт его в `SessionContext` (`api/session.ts`, хук `useSession`). Состояние не меняется за время жизни страницы:
  вход и выход - переходы на Keycloak и обратно. Ответа нет (сеть, старый gateway) - гость.
- Хуки личных данных (`useAlerts`, `useNotifications`, `usePortfolio`, `useBrokers`, `useTelegramStatus`) у гостя
  выключены (`enabled: authenticated`) - иначе колокольчики в таблице запросили бы `/api/alerts` и получили `401`.
- Шапка: гостю - «Войти», без кнопки Telegram и выхода; вошедшему - как раньше, в подсказке выхода имя.
  Разделы «Портфель» и «Уведомления» видны и гостю.
- `PortfolioPage`, `AlertsPage` у гостя рисуют `LoginRequired` (что здесь будет и «Войти»), колокольчик облигации -
  окно с тем же предложением.
- `login()` (`api/client.ts`) уходит на `/oauth2/authorization/keycloak?returnTo=<путь и query текущей страницы>`.
  `401` на любой запрос (сессия истекла) - тоже `login()`, с возвратом на текущую страницу.

## Тесты

- gateway: `SecurityConfigurationTest` (облигации без сессии уходят в invest-api, анонимный POST с CSRF и без,
  другие POST под `/api/bonds` - `401`, alerts и portfolio - `401`, `/api/session`, cookie `XSRF-TOKEN` без сессии,
  `returnTo` в сессии и отказ от чужого адреса), `LoginReturnTest`, `AnonymousRateLimiterTest`,
  `AnonymousRateLimitTest` (`429` по IP, вошедший без лимита), `PublicAccessDisabledTest`.
- UI: `pages/guest.test.tsx` - шапка гостя и вошедшего, портфель, уведомления и колокольчик без личных запросов,
  адрес входа с `returnTo`, разбор ответа `/api/session`.
