# INVEST-001: SEO-страницы

Статус: **в работе** - сделано 2026-11-10, этап 2 [дорожной карты](../../roadmap.md). Код не закоммичен; ветки - те, на
которых сейчас репозитории: invest-api `feature/INVEST-001`, invest-gateway `test`, invest-api-ui `feature/INV-001`,
invest-deploy `feature/INV-001` (отдельную ветку называет владелец).

> Как и публичный режим, на открытом адресе включать после решения владельца 0.1. SEO-страницы работают только вместе
> с `invest.gateway.public-access.enabled` и выключаются отдельно `invest.gateway.seo.enabled: false`.

## Цель

Поисковики индексируют страницы облигаций, эмитентов и подборок: по ссылке приходит готовый HTML с названием, ценой
и доходностью, а не пустой SPA.

## Объём

- **invest-api**
  - `GET /bonds/issuers/{id}` - эмитент (название, ИНН, выпуски из системы), `GET /bonds/issuers/{id}/news`;
  - эмитент в карточке облигации (`BondDto.issuer`) - для ссылки на его страницу;
  - подборки на готовых фильтрах: `GET /bonds/collections`, `GET /bonds/collections/{slug}` - «Самые доходные ОФЗ»,
    «Флоатеры», «Погашение в этом году»;
  - фильтр по дате погашения (`maturityDate`) в `BondParametersRequest` - на нём держится третья подборка;
  - `GET /bonds/sitemap` - тикеры, эмитенты с выпусками, подборки.
- **invest-gateway** - модуль `seo-service`: `/bonds`, `/bonds/{ticker}`, `/issuers/{id}`, `/collections/{slug}` -
  `index.html` UI с title, description, canonical, Open Graph и готовым содержимым; `/sitemap.xml`, `/robots.txt`.
  Пути открыты без входа и входят в лимит анонимных запросов.
- **invest-api-ui** - `usePageMeta` на каждой странице (те же теги при переходах внутри SPA), страницы эмитента
  `/issuers/:id` и подборки `/collections/:slug`, ссылка на эмитента в карточке, ссылки на подборки над списком.
- **invest-deploy** - ingress отправляет SEO-пути в gateway, gateway берёт `index.html` у nginx UI.

## Готово, когда

- [x] `curl` страницы облигации возвращает HTML с названием, ценой и доходностью - тест gateway
  `bondPageWithoutSessionHasMetadataAndContent`; на стенде k3d 2026-11-10 `curl /bonds/SU26250RMFS9` без сессии:
  title, description, canonical, `<h1>ОФЗ 26250</h1>`, цена в % и в рублях, «Доходность к погашению 16,78% годовых»,
  ссылка на эмитента; неизвестный тикер - `404`;
- [x] `sitemap.xml` перечисляет все облигации и эмитентов - тесты `sitemapListsBondsIssuersWithBondsAndCollections`
  (invest-api) и `sitemapListsAllPages` (gateway); на стенде - 1612 облигаций, 396 эмитентов, 3 подборки и `/bonds`;
- [x] Lighthouse SEO не ниже 90 - на стенде 100 у облигации, эмитента, подборки и списка (`lighthouse@12.8.2
  --only-categories=seo`). В браузере SPA заменяет серверное содержимое без ошибок в консоли, canonical и
  description - по одному.

## Ссылки

- [design.md](design.md) - как устроено, выбор способа рендеринга, отвергнутые варианты
- [business-rules.md](../../business-rules.md#подборки-облигаций) - правила подборок и фильтра по погашению
- [invest-gateway/docs/architecture.md](../../../../invest-gateway/docs/architecture.md#seo-страницы-feature-seo-service)
- [invest-api-ui/docs/architecture.md](../../../../invest-api-ui/docs/architecture.md#метаданные-страниц-seo)
