# Запуск и деплой invest-api

## Локальный запуск из IDE

Нужны JDK 25, Maven, PostgreSQL со схемой для приложения (Liquibase создаёт таблицы, но не схему).

Конфигурация:

| Файл                               | Что                                                                                 |
| ---------------------------------- | ----------------------------------------------------------------------------------- |
| `config/application.yml`           | шаблон конфигурации в git, Spring читает его из `./config` рабочей директории. Значения вида `$TOKEN`, `$DB_HOST` - заглушки, их нужно переопределить |
| `local_config/application-test.yml`| локальные настройки с токеном и БД, в git не попадает. Подключается в run configuration, например `--spring.config.additional-location=file:./local_config/ --spring.profiles.active=test` |
| `.env`                             | переменные для локального Postgres в docker (шаблон - `.env.example`), в git не попадает |

Что обязательно задать: `invest.connector.token` (токен T-Invest API), `spring.datasource.hikari.*`
(jdbc-url, username, password, `connection-init-sql: SET search_path TO <схема>`), `spring.liquibase.*`
(change-log `classpath:liquibase/db.changelog-master.xml`, default-schema, параметры `appUsername`, `schema`, `sql_schema`).
Локально приложение слушает `server.port: 8081`, invest-gateway ходит на `http://127.0.0.1:8081`.

Внешние HTTP-клиенты включаются флагами `spring.cloud.openfeign.client.config.<клиент>.enabled` и `url`: `cb-rf-client`
(https://www.cbr.ru/), `budget-org-client`, `moex-iss-client` (https://iss.moex.com/). Без включённого клиента
приложение не стартует: на нём держатся сервисы. В `local_config/application-test.yml` их тоже нужно прописать.

Лента новостей: `ru.invest.api.news.scheduler.enabled`, `...run-on-startup`, `...cron` (по умолчанию раз в 15 минут),
`...zone`. Первая загрузка - эмитенты (~30 запросов к Мосбирже) и новости за ~3 дня, меньше минуты.
Источники помимо ЦБ и Мосбиржи: `ru.invest.api.news.acra-url`, `...nkr-url` (пусто - агентство не грузится) и список
`...feeds` из `source` (значение `NewsSource`), `url`, `only-matched`. Новая лента СМИ - новая запись в `feeds` и
значение в `NewsSource` / `NewsSourceDto` / UI. АКРА отвечает по сертификату Минцифры: без него в truststore JVM
(локальный запуск) АКРА пропускается с ошибкой PKIX в логе, остальные источники грузятся.
`ru.invest.api.news.key-rate-meetings` - даты заседаний ЦБ по ключевой ставке; раз в год, когда ЦБ опубликует календарь
на следующий год, даты дописываются (без них в шапке ленты нет изменения ставки на заседании).

Кредитные рейтинги для скринера (`ru.invest.api.news.ratings`): `max-age` - сколько действует рейтинг без нового
действия агентства (по умолчанию `15m` - 15 месяцев), `history-on-empty` - при пустой таблице `credit_rating`
загрузить историю релизов (по умолчанию да), `history-max-pages` (300) и `history-page-pause` (300 мс) - обход
списка АКРА. Первая загрузка истории идёт в загрузке ленты после старта: ~150-200 страниц АКРА с паузой, 1-2 минуты;
лента и остальные шаги ждут её окончания. Перечитать историю вручную -
`POST /internal/rest/maintenance/news/ratings-history` (заголовок `From`, в фоне). Кривая ОФЗ для спреда и описание
купонов (база флоатера) берутся у той же `moex-iss-client`, своих настроек нет.

Шедулеры: `ru.invest.api.stock.supplier.scheduler.sync.enabled` (ночная цепочка по `sync.cron` - 6 полей Spring:
`секунды минуты часы день месяц день_недели`), `...sync.run-on-startup` - прогнать цепочку в фоне сразу после старта,
`...sync.steps.{bond,coupon,amortization,price,candle}` - отдельные шаги, `...scheduler.price.enabled` - частое обновление цен.
Для первого наполнения пустой БД должен быть включён шаг `bond`, иначе облигаций не будет. Полный прогон по пустой
БД занимает минуты (купоны ~1600 облигаций идут с лимитом T-Invest API), приложение в это время уже отвечает.
Первая загрузка свечей - вся история, около двух запросов на облигацию при 5 в секунду: ~10 минут; дальше ночной шаг
запрашивает только свежие свечи, около 5 минут.

Telegram-бот: `ru.invest.api.telegram.enabled` (по умолчанию выключен), токен бота от @BotFather - только в
переменной окружения `TELEGRAM_BOT_TOKEN`, имя бота без @ - `TELEGRAM_BOT_USERNAME`, включение -
`TELEGRAM_BOT_ENABLED=true`. Telegram в России заблокирован: либо Telegram идёт через TUN VPN (`proxy.type: NONE`, так
на ПК разработчика), либо через локальный прокси - `TELEGRAM_PROXY_TYPE=HTTP` (или `SOCKS`), `TELEGRAM_PROXY_HOST`,
`TELEGRAM_PROXY_PORT`. Настройка sing-box - [proxy.md](features/INVEST-001-telegram-bot/proxy.md). Бот должен
работать в одном экземпляре приложения (long polling). Если Telegram недоступен, приложение стартует и работает,
в логе раз в 5 минут `Telegram is unavailable`.

Уведомления: `ru.invest.api.alerts.scheduler.enabled` / `cron` / `zone` (проверка правил, по умолчанию 09:00 МСК, и
сразу после старта), `...dispatcher.enabled` / `interval` (разбор очереди, 30 с), `max-days`,
`give-up-after` (24 ч), `retention` (90 дней), `price-check-on-sync` (правила по цене и доходности после каждого
обновления цен; нужен включённый `ru.invest.api.stock.supplier.scheduler.price`, иначе цены обновляются только ночью),
`news-check-on-sync` (новости, рейтинги, ключевая ставка - после каждой загрузки ленты, `ru.invest.api.news.scheduler`). Без включённого Telegram-бота уведомления сразу получают `FAILED`.

Тарифы: `ru.invest.api.plans.default-plan` (тариф без подписки), `zone` (пояс последнего дня подписки, Москва),
`plans.<id>` - `title`, `description`, `paid`, `limits` (все из `ALERTS`, `PORTFOLIO_POSITIONS`, не меньше 0),
`features` (`CALENDAR_ICS`, `BROKER_IMPORT`), `price` и `period` - цена и срок для оплаты (только у платного, вместе). id тарифа
хранится в подписках - у выданного не менять. Ошибка в настройке не даёт
приложению стартовать. Выдать тариф: `PUT /internal/rest/maintenance/account/<sub Keycloak>/plan` с заголовком
`From` и телом `{"plan": "premium", "validUntil": "2027-01-31"}`; отозвать - `DELETE` того же адреса.

Оплата ([INVEST-001-payments](features/INVEST-001-payments/)): `ru.invest.api.payments` - `enabled`
(`PAYMENTS_ENABLED`, по умолчанию `false`), `provider` (`tbank`), `notification-url` (`PAYMENTS_NOTIFICATION_URL` -
публичный адрес gateway `https://<хост>/api/payments/notifications/tbank`, его же указать в кабинете Т-Кассы),
`success-url` / `fail-url` (`PAYMENTS_RETURN_URL` - страница «Тариф» UI, к ней добавляется `?payment={paymentId}`),
`offer-url` / `privacy-url` (`PAYMENTS_OFFER_URL`, `PAYMENTS_PRIVACY_URL` - оферта и политика персональных данных),
`renewal` - `interval` (шедулер, 1 ч), `charge-before` (за сколько до конца срока списывать, сутки), `retry-interval`
(сутки), `max-attempts` (3), `scheduler`. Терминал Т-Кассы - только из переменных окружения: `TBANK_TERMINAL_KEY`,
`TBANK_TERMINAL_PASSWORD` (пароль терминала - секрет: им подписываются запросы и проверяется webhook), адрес API -
`TBANK_API_URL` (по умолчанию `https://securepay.tinkoff.ru/v2`, тестовый терминал `...DEMO` - тот же адрес).
Включённая оплата без адресов или ключей не даёт приложению стартовать. Цена и срок - у тарифа: `plans.<id>.price`
(рубли) и `period` (`P1M`, `P1Y`); без них тариф не продаётся. Локально Т-Касса не достучится до webhook - статус
платежа узнаётся опросом со страницы «Тариф», автопродление при этом не включается (карту приносит только webhook).
Запустить автопродление сейчас: `POST /internal/rest/maintenance/payments/renewals`.

Импорт от брокера ([INVEST-001-broker-import](features/INVEST-001-broker-import/)): `ru.invest.api.imports` -
`token-encryption-key` (`IMPORT_TOKEN_KEY`: 32 случайных байта в Base64, `openssl rand -base64 32`; секрет - им
шифруются токены T-Invest пользователей; пусто - импорт по токену выключен, импорт файлов работает; ключ не той длины
не даёт стартовать), `preview-ttl` (7 дней), `max-items` (5000 сделок), `max-file-size` (5 МБ; предел Spring -
`spring.servlet.multipart.max-file-size`, 6 МБ), `zone` (дата сделки, Москва), `tinvest.target` (адрес T-Invest API;
песочница - `sandbox-invest-public-api.tinkoff.ru:443`), `tinvest.timeout`, `tinvest.max-operations` (20 000 со счёта),
`tinvest.broker-name`. **Смена ключа** делает сохранённые токены нечитаемыми: пользователи увидят «подключите токен
заново»; старый ключ не храните в репозитории. Импорт - фича тарифа `BROKER_IMPORT` (`plans.<id>.features`).

Брокеры-партнёры (блок «Купить у брокера»): `ru.invest.api.broker-referral.partners` - список `id`, `name`,
`url-template` (https, подстановки `{ticker}` `{isin}` `{erid}`), `advertiser` (наименование и ИНН для маркировки
рекламы), `erid`, `enabled`. По умолчанию пусто - блок не показывается. `id` не менять: по нему считаются переходы.
Ошибка в настройке партнёра не даёт приложению стартовать. Пример:

```yaml
ru.invest.api.broker-referral.partners:
  - id: example-broker
    name: Пример Брокер
    url-template: https://broker.example/bonds/{isin}?utm_source=invest&erid={erid}
    advertiser: АО "Пример Брокер", ИНН 0000000000
    erid: <токен из ЕРИР>
```

Swagger: http://localhost:8081/swagger-ui.html.

## Локальный стенд в k3d

Все сервисы (invest-api, gateway, UI, Keycloak, Postgres) в кластере k3d в WSL поднимает
[invest-deploy](../../invest-deploy/docs/deployment.md): `.\deploy.ps1 api` в каталоге invest-deploy собирает
invest-api и выкатывает его. Профиль `k8s` для стенда лежит в invest-deploy, базовый `config/application.yml` берётся
из этого репозитория.

## Образ

Сборка раскладывает приложение для Docker в `app/target/dockerbuild` (`mvn package`, плагины `maven-dependency-plugin`
и `maven-antrun-plugin` в корневом pom):

- `ext/` - сторонние библиотеки: меняются редко, идут отдельным слоем;
- `lib/` - модули проекта и сам `app`.

Запуск: `java -cp '/app/lib/*:/app/ext/*' ru.invest.api.InvestApplication`. В truststore JVM нужен корневой
сертификат Минцифры (им подписан сертификат invest-public-api.tinkoff.ru).

## CI

`.github/workflows/ci-cd.yml`:

| Событие                        | Что                                                         |
| ------------------------------ | ----------------------------------------------------------- |
| pull request в master/develop/uat | сборка, checkstyle, тесты                                |
| push в develop/uat             | + образ `ghcr.io/monbar921/invest-api`, теги `<short sha>`, `<ветка>` |
| push в master                  | + тег `latest`                                              |

Деплой из CI не делается.

> Сейчас job образа сломан: `Dockerfile` в корне репозитория удалён в коммите `856eb1d`, а `docker/build-push-action`
> собирает с `context: .`. Рабочий Dockerfile для стенда - `invest-deploy/docker/java.Dockerfile`.

## Kubernetes (удалённый кластер)

Манифесты на Kustomize в `k8s/`, образ из ghcr.io:

```
k8s/
  base/                 Deployment, Service, Ingress (nginx), ConfigMap из config.env
  overlays/
    develop/            namespace invest-api-develop
    uat/                namespace invest-api-uat
    prod/               namespace invest-api-prod
  secret.example.yaml   шаблон секрета
```

Overlay задаёт namespace, тег образа (`images.newTag`), хост ingress и отличия конфига.

### Первый деплой окружения

На примере `develop`:

1. Ресурсы (под не поднимется, пока нет секретов - это нормально):
   ```bash
   kubectl apply -k k8s/overlays/develop
   ```
2. Секрет приложения (см. `k8s/secret.example.yaml`):
   ```bash
   kubectl create secret generic invest-api-secret -n invest-api-develop \
     --from-literal=TOKEN=<tinkoff-token> \
     --from-literal=DB_URL=jdbc:postgresql://<host>:5432/<db> \
     --from-literal=DB_USERNAME=<user> \
     --from-literal=DB_PASSWORD=<password>
   ```
3. Доступ к приватному ghcr.io (PAT с правом `read:packages`):
   ```bash
   kubectl create secret docker-registry ghcr-secret -n invest-api-develop \
     --docker-server=ghcr.io --docker-username=Monbar921 --docker-password=<PAT>
   ```
4. Перезапуск:
   ```bash
   kubectl rollout restart deployment/invest-api -n invest-api-develop
   kubectl rollout status deployment/invest-api -n invest-api-develop
   ```

### Обновление версии

Тег в `k8s/overlays/<окружение>/kustomization.yaml` (лучше short sha - деплой воспроизводим, откат - смена тега),
затем `kubectl apply -k k8s/overlays/<окружение>`. С тегом ветки (`develop`, `latest`) apply изменений не увидит,
нужен `kubectl rollout restart`. Изменения `config.env` перезапускают поды сами: к имени ConfigMap добавляется хэш.

### Проверка

```bash
kubectl kustomize k8s/overlays/develop          # что будет применено
kubectl logs -l app=invest-api -n invest-api-develop --tail=100
kubectl port-forward svc/invest-api 8080:80 -n invest-api-develop
```

`ImagePullBackOff` - проблема с `ghcr-secret`, `CrashLoopBackOff` - логи (обычно БД или токен).

> Манифесты k8s/ отстают от кода: env `TOKEN` и `DB_*` из секрета не совпадают с ключами конфига
> (`invest.connector.token`, `spring.datasource.hikari.*`), а схема и Liquibase не настроены. Перед использованием
> их нужно сверить с профилем `k8s` в invest-deploy.

`spring.jpa.open-in-view: false` (в `config/application.yml`): с ним по умолчанию `true` EntityManager жил весь
HTTP-запрос, и ручная синхронизация свечей копила в нём все сохранённые сущности - под падал по памяти
(`OOMKilled` на лимите 1 ГБ).
