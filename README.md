# invest-api

Сервис облигаций: синхронизирует облигации, купоны, амортизацию и цены из
[T-Invest API](https://developer.tbank.ru/invest/intro/intro), считает купонную доходность и доходность к погашению
и к оферте, отдаёт список с фильтрами и сортировкой. Spring Boot 4, Java 25, PostgreSQL.

```bash
mvn verify     # checkstyle + тесты (нужен Docker для Testcontainers)
```

Документация - в [docs/](docs/README.md): архитектура, бизнес-правила, тесты, запуск и деплой, фичи.
