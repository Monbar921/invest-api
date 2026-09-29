# Деплой в Kubernetes

CI (`.github/workflows/ci-cd.yml`) только собирает и публикует образ `ghcr.io/monbar921/invest-api`,
деплой выполняется руками. Манифесты собраны на Kustomize (встроен в `kubectl`):

```
k8s/
  base/                 общие манифесты: Deployment, Service, Ingress, ConfigMap (config.env)
  overlays/
    develop/            namespace invest-api-develop
    uat/                namespace invest-api-uat
    prod/               namespace invest-api-prod
  secret.example.yaml   шаблон секрета
```

Overlay задаёт окружению namespace, тег образа, хост ingress и отличия конфига.

## Теги образа

| Ветка     | Теги                           |
|-----------|--------------------------------|
| `develop` | `<short sha>`, `develop`       |
| `uat`     | `<short sha>`, `uat`           |
| `master`  | `<short sha>`, `master`, `latest` |

Short sha виден в логе шага **Build and push** и на странице пакета в GitHub.

## Первый деплой окружения

Ниже `develop`, для остальных окружений замените имя.

1. Namespace и остальные ресурсы (под не поднимется, пока нет секретов, это нормально):
   ```bash
   kubectl apply -k k8s/overlays/develop
   ```
2. Секрет приложения (см. `secret.example.yaml`):
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
4. Перезапуск, чтобы под подхватил секреты:
   ```bash
   kubectl rollout restart deployment/invest-api -n invest-api-develop
   kubectl rollout status deployment/invest-api -n invest-api-develop
   ```

Схема БД из `application.yml` (`invest_api_test`) должна существовать заранее: Liquibase создаёт таблицы, но не схему.

## Обновление версии

1. Укажите тег в `k8s/overlays/<окружение>/kustomization.yaml` (`images.newTag`), например `cac52c3`.
2. Примените:
   ```bash
   kubectl apply -k k8s/overlays/develop
   kubectl rollout status deployment/invest-api -n invest-api-develop
   ```

Тег лучше задавать через sha: деплой воспроизводим, откат - смена тега обратно.
Если оставить тег ветки (`develop`, `latest`), `apply` не увидит изменений - нужен
`kubectl rollout restart deployment/invest-api -n <namespace>`.

Изменения `base/config.env` или `configMapGenerator` в overlay перезапускают поды сами:
к имени ConfigMap добавляется хэш содержимого.

## Проверка

```bash
kubectl kustomize k8s/overlays/develop          # что будет применено
kubectl get pods -n invest-api-develop
kubectl logs -l app=invest-api -n invest-api-develop --tail=100
kubectl port-forward svc/invest-api 8080:80 -n invest-api-develop
```

После `port-forward`: http://localhost:8080/actuator/health, http://localhost:8080/swagger-ui.html.

`ImagePullBackOff` - проблема с `ghcr-secret`, `CrashLoopBackOff` - смотрите логи (обычно БД или токен).
