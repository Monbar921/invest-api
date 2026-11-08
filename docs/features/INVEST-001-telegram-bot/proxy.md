# INVEST-001: Telegram через прокси (sing-box)

Telegram в России заблокирован, а invest-api запускается на своём ПК. Поэтому запросы бота к `api.telegram.org`
идут через локальный sing-box и VPS за рубежом, а **всё остальное (T-Invest API, Мосбиржа, ЦБ) - напрямую**:
с зарубежного IP эти сервисы могут отказывать или ограничивать.

```
invest-api ──HTTP CONNECT──▶ sing-box 127.0.0.1:2080 ──VLESS──▶ VPS ──▶ api.telegram.org
invest-api ──напрямую──────▶ T-Invest, ISS Мосбиржи, ЦБ
```

Прокси в приложении - HTTP (`CONNECT api.telegram.org:443`), не SOCKS: так в sing-box приходит имя хоста, и DNS
разрешается на VPS. Местный DNS для заблокированного домена может вернуть неверный адрес.

## Вход только для Telegram

Inbound `telegram-in` (тип `mixed` - HTTP и SOCKS5 на одном порту) слушает только `127.0.0.1` и пропускает только
домены Telegram, остальное отклоняет. Если приложение по ошибке пустит через прокси другой запрос, он не уйдёт на VPS.

Формат конфига - sing-box 1.12+ (действия в правилах `route`, новый формат DNS-серверов).

### Вариант А: добавить в уже работающий конфиг sing-box

Подходит, если sing-box с VPN и так запущен постоянно. Outbound к VPS уже есть - нужен только его `tag`
(ниже - `vps`).

В `inbounds` добавить:

```json
{
  "type": "mixed",
  "tag": "telegram-in",
  "listen": "127.0.0.1",
  "listen_port": 2080
}
```

В начало `route.rules` (до остальных правил, порядок важен):

```json
{ "inbound": "telegram-in", "action": "sniff" },
{
  "inbound": "telegram-in",
  "domain_suffix": ["telegram.org", "t.me"],
  "action": "route",
  "outbound": "vps"
},
{ "inbound": "telegram-in", "action": "reject" }
```

**Если sing-box работает в режиме TUN** (весь трафик ПК идёт через него): проверьте, что T-Invest, Мосбиржа и ЦБ
идут `direct`, например правилом по `domain_suffix: [".ru"]` или по rule-set `geosite-category-ru` /
`geoip-ru`. Иначе синхронизация облигаций пойдёт через VPS.

### Вариант В: Telegram через TUN, без прокси в приложении (используется на ПК разработчика)

Если sing-box и так работает в режиме TUN по схеме «`route.final: direct`, к VPS - только перечисленные домены»,
отдельный inbound не нужен: домены и IPv4-сети Telegram добавляются в правило к VPS, а домены - ещё и в `dns.rules`
на удалённый DNS (DoH через VPS), чтобы местный DNS не подменил адрес. В invest-api тогда `proxy.type: NONE`.

В `dns.rules` - к доменам, которые уже идут на удалённый DNS:

```json
"telegram.org", "t.me", "telegram.me", "telegra.ph", "telesco.pe", "tdesktop.com"
```

В конец `route.rules` (`<tag>` - outbound к VPS):

```json
{
  "domain_suffix": ["telegram.org", "t.me", "telegram.me", "telegra.ph", "telesco.pe", "tdesktop.com"],
  "ip_cidr": [
    "91.108.4.0/22", "91.108.8.0/22", "91.108.12.0/22", "91.108.16.0/22", "91.108.20.0/22",
    "91.108.56.0/22", "95.161.64.0/20", "149.154.160.0/20", "185.76.151.0/24"
  ],
  "outbound": "<tag>"
}
```

Проверено 2026-11-08 (sing-box 1.14.2 для Windows, hysteria2 к VPS): `api.telegram.org` - 302 через VPS, T-Invest,
ЦБ и Мосбиржа - напрямую. Полный вариант (этот + inbound `telegram-in` + IPv6-сети Telegram) на том же ПК не
запустился: `start inbound/tun[tun-in]: configure tun interface: The system cannot find the file specified`. В тот
момент был запущен AmneziaVPN (второй wintun-адаптер), так что причина не установлена; одновременно два VPN с TUN
не запускать.

### Вариант Б: отдельный экземпляр только для бота

Подходит, если VPN включается вручную, а бот должен работать всегда. Файл, например,
`C:\sing-box\telegram-proxy.json`. Outbound `vps` - **скопировать из своего рабочего конфига или подписки** как есть;
ниже пример для VLESS + Reality, значения в `<...>` - свои.

```json
{
  "log": { "level": "warn", "timestamp": true },
  "dns": {
    "servers": [{ "type": "local", "tag": "local" }]
  },
  "inbounds": [
    {
      "type": "mixed",
      "tag": "telegram-in",
      "listen": "127.0.0.1",
      "listen_port": 2080
    }
  ],
  "outbounds": [
    {
      "type": "vless",
      "tag": "vps",
      "server": "<VPS_HOST>",
      "server_port": 443,
      "uuid": "<UUID>",
      "flow": "xtls-rprx-vision",
      "tls": {
        "enabled": true,
        "server_name": "<SNI>",
        "utls": { "enabled": true, "fingerprint": "chrome" },
        "reality": {
          "enabled": true,
          "public_key": "<PUBLIC_KEY>",
          "short_id": "<SHORT_ID>"
        }
      }
    }
  ],
  "route": {
    "default_domain_resolver": "local",
    "rules": [
      { "inbound": "telegram-in", "action": "sniff" },
      {
        "inbound": "telegram-in",
        "domain_suffix": ["telegram.org", "t.me"],
        "action": "route",
        "outbound": "vps"
      },
      { "inbound": "telegram-in", "action": "reject" }
    ],
    "final": "vps"
  }
}
```

`dns` с `local` нужен только чтобы разрешить адрес самого VPS, если в `server` указано имя, а не IP.

Если на ПК одновременно работает основной sing-box в режиме TUN, соединение второго экземпляра к VPS попадёт в TUN
(двойной туннель). Тогда либо вариант А, либо правило `direct` для IP VPS в основном конфиге.

Запуск и автозапуск на Windows:

```bash
sing-box check -c C:\sing-box\telegram-proxy.json
```

```bash
sing-box run -c C:\sing-box\telegram-proxy.json
```

Для автозапуска - служба Windows через `nssm` или задача в Планировщике «При входе в систему».

## Проверка

Команды - для PowerShell (`curl.exe`, а не алиас `curl` = `Invoke-WebRequest`).

```bash
curl.exe -x http://127.0.0.1:2080 -s -o NUL -w "%{http_code}\n" https://api.telegram.org
```

Ожидается `302` (Telegram перенаправляет на страницу документации). Чужой домен должен отклоняться:

```bash
curl.exe -x http://127.0.0.1:2080 -s -o NUL -w "%{http_code}\n" https://ya.ru
```

Ожидается ошибка соединения (`000`).

Проверка токена бота через прокси (токен - из переменной окружения, в команды и логи его не вписывать):

```bash
curl.exe -x http://127.0.0.1:2080 "https://api.telegram.org/bot$env:TELEGRAM_BOT_TOKEN/getMe"
```

## Настройки invest-api

```yaml
ru.invest.api.telegram:
  enabled: true
  bot-token: ${TELEGRAM_BOT_TOKEN}     # от @BotFather; только переменная окружения, не в репозиторий
  bot-username: <имя бота без @>       # для ссылки t.me/<bot>?start=<token>
  proxy:
    type: HTTP                         # HTTP | SOCKS | NONE (NONE - Telegram идёт через TUN, вариант В)
    host: 127.0.0.1
    port: 2080
```

Прокси получает только HTTP-клиент Telegram (OkHttp в `telegrambots`): и long polling, и отправка сообщений.
Остальные клиенты приложения прокси не видят.

Если sing-box не запущен или VPS недоступен, бот не теряет уведомления: они ждут в очереди `notification`
(см. [design.md](design.md#доставка-очередь-уведомлений)) и уходят, когда связь вернётся. В UI статус «Telegram
недоступен».

Пользователям Telegram тоже нужен рабочий доступ к Telegram на телефоне - это решается на их стороне.

## В Kubernetes (позже)

Тот же sing-box - sidecar-контейнером в поде invest-api (конфиг и данные VPS - из Secret), прокси
`127.0.0.1:2080`. Когда стенд переедет в k3d, перенести этот раздел в [deployment.md](../../deployment.md).
