# Wallet Service

Высоконагруженный REST-сервис для управления кошельками. Выдерживает 1000+ RPS на один кошелёк без ошибок 50x.

## Технологии

- **Java 17**
- **Spring Boot 3.3**
- **PostgreSQL 16**
- **Liquibase** — миграции базы данных
- **Testcontainers** — интеграционные тесты с реальной БД
- **Docker / Docker Compose** — контейнеризация
- **Swagger/OpenAPI** — документация API

## Архитектура

### Проблема конкурентности

При 1000 RPS на один кошелёк стандартные подходы не работают:

| Подход              | Проблема                           |
|---------------------|------------------------------------|
| `SELECT FOR UPDATE` | Очередь из запросов, таймауты, 50x |
| Optimistic locking  | Лавина повторных попыток           |
| Атомарный `UPDATE`  | Contention на уровне БД            |

### Решение: In-Memory Buffer

Запросы агрегируются в памяти и сбрасываются в БД одним batch-запросом:

```
Клиент → [HTTP] → Буфер (ConcurrentHashMap) → [100 мс] → UPDATE wallet SET balance = balance + totalDelta
```

- **1000 запросов/сек** → **10 batch-операций/сек** вместо 1000 отдельных UPDATE
- **Задержка ответа** < 1 мс (клиент получает `202 Accepted` сразу)

### Отказоустойчивость

- **Transaction Recovery** — зависшие транзакции автоматически перезапускаются
- **Retry с backoff** — временные сбои БД обрабатываются без потери данных
- **Полный аудит** — каждая операция сохраняется в таблице `transactions`

### Production-решение: Kafka

Для production-среды вместо In-Memory Buffer рекомендуется использовать **Apache Kafka**:

```
Клиент → [HTTP] → Kafka (топик wallet-transactions) → Consumer → UPDATE wallet
```

**Преимущества Kafka:**

- **Durability** — сообщения не теряются при падении сервера (персистентность на диск)
- **Replayability** — возможность перечитать историю транзакций
- **Observability** — consumer lag показывает здоровье системы
- **Ecosystem** — другие сервисы могут подписываться на события кошелька (нотификации, аналитика, fraud-детектор)

In-Memory Buffer оставлен для тестового задания как демонстрация решения проблемы конкуренции с минимальными
зависимостями.

## Быстрый старт

### 1. Клонировать репозиторий

```bash
git clone https://github.com/leonid353/wallet-service.git
cd wallet-service
```

### 2. Запустить через Docker Compose

```bash
docker-compose up -d
```

Сервис будет доступен на `http://localhost:8080`.

### 3. Проверить здоровье

```bash
curl http://localhost:8080/actuator/health
```

## API

### Создать кошелёк

```http
POST /api/v1/wallets
Content-Type: application/json

{
"initialBalance": 100.00
}
```

Ответ: `201 Created`

### Получить баланс

```http
GET /api/v1/wallets/{walletId}
```

Ответ: `200 OK`

### Операция с кошельком

```http
POST /api/v1/wallet
Content-Type: application/json

{
"walletId": "550e8400-e29b-41d4-a716-446655440000",
"operationType": "DEPOSIT",
"amount": 1000.00
}
```

Ответ: `202 Accepted`

### История транзакций

```http
GET /api/v1/wallets/{walletId}/transactions?page=0&size=20
```

## Конфигурация

Все параметры настраиваются через переменные окружения (`.env` файл) без пересборки контейнера:

### База данных

| Переменная          | По умолчанию | Описание             |
|---------------------|--------------|----------------------|
| `POSTGRES_DB`       | walletdb     | Название базы данных |
| `POSTGRES_USER`     | username     | Пользователь БД      |
| `POSTGRES_PASSWORD` | password     | Пароль БД            |

### Сервер приложения

| Переменная            | По умолчанию | Описание                               |
|-----------------------|--------------|----------------------------------------|
| `SERVER_PORT`         | 8080         | Порт приложения                        |
| `TOMCAT_MAX_THREADS`  | 200          | Максимальное количество потоков Tomcat |
| `TOMCAT_MIN_THREADS`  | 10           | Минимальное количество потоков         |
| `TOMCAT_ACCEPT_COUNT` | 100          | Размер очереди запросов                |
| `TOMCAT_CONN_TIMEOUT` | 20000        | Таймаут соединения (мс)                |

### Пул соединений HikariCP

| Переменная             | По умолчанию | Описание                                        |
|------------------------|--------------|-------------------------------------------------|
| `DB_POOL_MAX_SIZE`     | 20           | Максимальный размер пула                        |
| `DB_POOL_MIN_IDLE`     | 5            | Минимальное количество простаивающих соединений |
| `DB_POOL_CONN_TIMEOUT` | 30000        | Таймаут ожидания соединения (мс)                |
| `DB_POOL_IDLE_TIMEOUT` | 600000       | Таймаут простоя соединения (мс)                 |
| `DB_POOL_MAX_LIFETIME` | 1800000      | Максимальное время жизни соединения (мс)        |

### JPA / Hibernate

| Переменная       | По умолчанию | Описание                         |
|------------------|--------------|----------------------------------|
| `JPA_SHOW_SQL`   | false        | Показывать SQL в логах           |
| `JPA_FORMAT_SQL` | false        | Форматировать SQL                |
| `JPA_BATCH_SIZE` | 20           | Размер пакета для batch-операций |

### Обработка транзакций

| Переменная            | По умолчанию | Описание                               |
|-----------------------|--------------|----------------------------------------|
| `WALLET_LOCK_TIMEOUT` | 2000         | Таймаут пессимистичной блокировки (мс) |
| `WALLET_MAX_RETRIES`  | 3            | Количество повторных попыток при сбоях |
| `WALLET_RETRY_DELAY`  | 100          | Задержка между попытками (мс)          |

### Буфер (In-Memory Buffer)

| Переменная                  | По умолчанию | Описание                                          |
|-----------------------------|--------------|---------------------------------------------------|
| `BUFFER_FLUSH_INTERVAL`     | 100          | Интервал сброса буфера (мс)                       |
| `BUFFER_MAX_SIZE`           | 10000        | Максимальный размер очереди на кошелёк            |
| `BUFFER_EMPTY_LOG_INTERVAL` | 500          | Интервал логирования пустого буфера (в итерациях) |

### Восстановление зависших транзакций

| Переменная                | По умолчанию | Описание                                        |
|---------------------------|--------------|-------------------------------------------------|
| `RECOVERY_INTERVAL`       | 300000       | Интервал проверки зависших транзакций (мс)      |
| `RECOVERY_MAX_RETRIES`    | 3            | Максимальное количество попыток восстановления  |
| `STUCK_THRESHOLD_MINUTES` | 5            | Возраст транзакции для признания зависшей (мин) |

### Логирование

| Переменная             | По умолчанию | Описание                           |
|------------------------|--------------|------------------------------------|
| `LOG_LEVEL`            | INFO         | Общий уровень логирования          |
| `APP_LOG_LEVEL`        | DEBUG        | Уровень логирования приложения     |
| `WEB_LOG_LEVEL`        | INFO         | Уровень логирования веб-слоя       |
| `SQL_LOG_LEVEL`        | WARN         | Уровень логирования SQL            |
| `SQL_PARAMS_LOG_LEVEL` | WARN         | Уровень логирования параметров SQL |

## Документация API

Swagger UI доступен после запуска:

```
http://localhost:8080/swagger-ui.html
```

OpenAPI спецификация:

```
http://localhost:8080/v3/api-docs
```

## Тестирование

### Все тесты

```bash
mvn clean test
```

### Нагрузочный тест (1000 RPS)

```bash
mvn test -Dtest=WalletTransactionBufferTest
```

Тесты используют **Testcontainers** — автоматически поднимают PostgreSQL в контейнере.

## Мониторинг

Actuator endpoints:

| Endpoint                     | Описание             |
|------------------------------|----------------------|
| `/actuator/health`           | Статус сервиса       |
| `/actuator/health/readiness` | Готовность к трафику |
| `/actuator/health/liveness`  | Живучесть            |
| `/actuator/metrics`          | Метрики              |

## Структура проекта

```
src/main/java/com/github/leonid353/
├── buffer/             # Буфер для 1000 RPS, восстановление зависших транзакций
├── common/ 
│   ├── config/         # Конфигурация
│   ├── error/          # Обработка ошибок
│   └── exception/      # Исключения
├── wallet/
│   ├── controller/     # REST контроллеры
│   ├── dto/            # DTO
│   ├── model/          # Сущности JPA
│   ├── repository/     # Spring Data репозитории
│   └── service/        # Бизнес-логика
└── transaction/
    ├── dto/            # DTO
    ├── model/          # Сущность Transaction
    └── repository/     # Репозиторий транзакций
```

## База данных

Миграции Liquibase:

- `001-init-schema.yaml` — создание таблиц `wallets` и `transactions`

Схема:

```
wallets (
id UUID PRIMARY KEY,
balance DECIMAL(19,2) NOT NULL CHECK (balance >= 0),
created_at TIMESTAMPTZ,
updated_at TIMESTAMPTZ
)

transactions (
id UUID PRIMARY KEY,
wallet_id UUID FK → wallets(id),
amount DECIMAL(19,2),
type VARCHAR(10),      -- DEPOSIT / WITHDRAW
status VARCHAR(20),    -- PENDING / COMPLETED / FAILED
created_at TIMESTAMPTZ,
completed_at TIMESTAMPTZ,
error_message VARCHAR(500),
retry_count INT DEFAULT 0
)
```

## Результаты нагрузочного тестирования

Тесты проводились на Testcontainers PostgreSQL 16.1 с оптимизированными настройками:

```properties
# Оптимизация для нагрузочных тестов
DB_POOL_MAX_SIZE=30
DB_POOL_MIN_IDLE=10
DB_POOL_CONN_TIMEOUT=10000
BUFFER_FLUSH_INTERVAL=50
```

| Тест                     | Запросов | Время     | RPS       | Тип нагрузки               |
|--------------------------|----------|-----------|-----------|----------------------------|
| 1000 депозитов           | 1 000    | 976 мс    | **1025**  | Только DEPOSIT             |
| 1000 смешанных           | 1 000    | 1 020 мс  | **980**   | 500 DEPOSIT + 500 WITHDRAW |
| 5000 депозитов           | 5 000    | 4 312 мс  | **1 160** | Только DEPOSIT             |
| 10000 депозитов          | 10 000   | 8 863 мс  | **1128**  | Только DEPOSIT             |
| 10000 смешанных (10 сек) | 10 000   | 11 150 мс | **897**   | По 1000/сек (500+500)      |

```note
Примечание: 
В production-среде с выделенным PostgreSQL, многопоточным пулом Async и заменой In-Memory Buffer на Kafka ожидается 
стабильно 1000+ RPS на любых типах нагрузки.
```