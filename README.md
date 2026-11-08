# Парсер цен на товары (вариант 2)

Многопоточный сервис на Spring Boot: принимает ссылки на товары, обходит их параллельно и по расписанию, собирает заголовок, краткое описание, цену и дату публикации, сохраняет всё в H2 и отдаёт через REST.

## Запуск

Нужна JDK 17 или новее.

**В IntelliJ IDEA:** откройте папку проекта, дождитесь импорта Maven и запустите метод `main` в классе `com.example.priceparser.PriceParserApplication`.

**Из консоли:**

```bash
./mvnw spring-boot:run
```

В Windows PowerShell вместо `./mvnw` используйте `.\mvnw.cmd`. Если Java нет в PATH, сначала укажите путь к JDK: `$env:JAVA_HOME="путь\к\jdk"`. Если порт 8080 занят, добавьте `-Dspring-boot.run.arguments=--server.port=8081` и замените порт в примерах ниже.

## Как проверить

**1. Результаты обхода по расписанию.** Через 2 секунды после старта планировщик обойдёт 8 страниц демо-магазина. Откройте в браузере:

http://localhost:8080/answer

В поле `threadName` видно, какой поток пула обработал страницу.

**2. Сортировка, фильтры и пагинация:**

- http://localhost:8080/answer?sortBy=price&direction=asc
- http://localhost:8080/answer?currency=RUB&minPrice=30000&sortBy=title
- http://localhost:8080/answer?q=наушники
- http://localhost:8080/answer?size=3&page=1

**3. Свои ссылки.** Запрос ставит ссылки на отслеживание, сразу обходит их параллельно и возвращает итог по каждой.

Linux, macOS, Git Bash или cmd:

```bash
curl -X POST http://localhost:8080/api/parse -H "Content-Type: application/json" -d "{\"urls\":[\"https://books.toscrape.com/catalogue/a-light-in-the-attic_1000/index.html\",\"http://localhost:8080/demo-shop/products/1\",\"http://localhost:8080/demo-shop/no-price\",\"bad url\"]}"
```

Windows PowerShell (там `curl` — псевдоним другой команды, поэтому пример выше не сработает):

```powershell
$body = '{"urls":["https://books.toscrape.com/catalogue/a-light-in-the-attic_1000/index.html","http://localhost:8080/demo-shop/products/1","http://localhost:8080/demo-shop/no-price","bad url"]}'
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/parse -ContentType 'application/json' -Body $body | ConvertTo-Json -Depth 4
```

В этом примере первые две ссылки вернут `SUCCESS`, страница без цены — `FAILED`, а некорректная ссылка — `REJECTED`. Первой ссылке нужен интернет.

Пример ответа для одной ссылки:

```json
{
  "url": "http://localhost:8080/demo-shop/products/1",
  "status": "SUCCESS",
  "error": null,
  "product": {
    "id": 21,
    "url": "http://localhost:8080/demo-shop/products/1",
    "title": "Ноутбук Aero 14",
    "description": "Лёгкий ноутбук с экраном 14 дюймов и автономностью до 12 часов.",
    "price": 86120.54,
    "currency": "RUB",
    "publishedAt": "2026-03-12T06:00:00Z",
    "publishedAtSource": "PAGE",
    "parsedAt": "2026-11-05T19:28:16.294462Z",
    "threadName": "parser-worker-3"
  }
}
```

**4. История цены.** Обход повторяется каждые 60 секунд, а цена в демо-магазине при каждом запросе немного меняется. Подождите минуту и откройте:

http://localhost:8080/answer?url=http://localhost:8080/demo-shop/products/1&latestOnly=false

**5. Состояние пула и список ссылок:**

- http://localhost:8080/api/status — потоки, очередь, счётчики и число запусков по расписанию
- http://localhost:8080/api/urls — все ссылки с итогом последней проверки

**6. База данных.** Откройте http://localhost:8080/h2-console и укажите JDBC URL `jdbc:h2:mem:pricedb`, пользователя `sa` и пустой пароль. Таблицы: `PRICE_RECORD` и `TRACKED_URL`.

**7. Файл результатов.** Демон-поток `result-file-writer` пишет каждый результат в `data/parse-results.jsonl` в папке запуска. Файл сохраняется после перезапуска, а последние записи можно посмотреть здесь:

http://localhost:8080/api/results-file?limit=20

## API

| Метод | Адрес | Назначение |
|---|---|---|
| POST | `/api/parse` | Добавить ссылки и сразу обойти их, до 100 за запрос |
| GET | `/answer` | Результаты с сортировкой, фильтрами и пагинацией |
| GET | `/api/urls` | Отслеживаемые ссылки и итог последней проверки |
| GET | `/api/status` | Метрики пула и счётчики |
| GET | `/api/results-file?limit=20` | Последние записи файла результатов, `limit` от 1 до 1000 |
| GET | `/demo-shop/products/{id}` | Страницы демо-магазина, `id` от 1 до 8 |

Параметры `/answer`:

| Параметр | По умолчанию | Описание |
|---|---|---|
| `page` | `0` | Номер страницы |
| `size` | `20` | Размер страницы, от 1 до 100 |
| `sortBy` | `parsedAt` | `price`, `title`, `publishedAt` или `parsedAt` |
| `direction` | `desc` | `asc` или `desc` |
| `latestOnly` | `true` | `true` — последняя запись по каждой ссылке, `false` — вся история |
| `q` | | Поиск по заголовку и описанию |
| `url` | | Только указанная ссылка |
| `currency` | | Код валюты, например `RUB` |
| `minPrice`, `maxPrice` | | Диапазон цены |

Цены в разных валютах сортируются как числа, без пересчёта. Чтобы сравнивать цены между собой, добавьте фильтр `currency`.

Статусы обработки ссылки: `SUCCESS`, `FAILED`, `REJECTED`, `SKIPPED_IN_PROGRESS` (ссылку уже обрабатывает другой поток) и `TIMEOUT` (задача не уложилась в `parser.task-timeout`, результат может появиться позже).

## Как выполнены требования

| Критерий или требование | Где реализовано |
|---|---|
| Сервис на Spring Boot с unit-тестами | Всё приложение; тесты в `src/test` |
| Простые потоки `Thread` и `Runnable` | `ResultFileLog` реализует `Runnable` и работает в собственном `Thread` |
| Кастомный `ExecutorService` | `ThreadPoolExecutor` с именованными потоками и ограниченной очередью: `ConcurrencyConfig` |
| Собственный планировщик | `ScheduledExecutorService`: `ParsingScheduler` |
| Блокирующие структуры | `LinkedBlockingQueue`: `ResultFileLog`; `ArrayBlockingQueue`: `ConcurrencyConfig` |
| Примитивы синхронизации | `Semaphore`: `ParsingService`; `ReentrantLock`: `ResultFileLog` |
| Хранилище в собственном файле | `data/parse-results.jsonl`: `ResultFileLog` |
| Демон-потоки и логирование | `result-file-writer`: `ResultFileLog`; `parser-scheduler`: `ParsingScheduler` |
| H2 и JPA | Сущности `PriceRecord` и `TrackedUrl`, репозитории Spring Data |
| Эндпоинт результатов | `GET /answer`: `AnswerController` |
| Фильтрация и сортировка через `parallelStream` | `AnswerService` |
| Межсервисное взаимодействие | `WebClient` из Spring WebFlux: `PageFetcher`. Демо-магазин играет роль внешнего сервиса |
| Потокобезопасные структуры | `ConcurrentHashMap` и `ConcurrentHashMap.newKeySet()`, `AtomicLong`, `AtomicReference`: `ParsingService` |

## Как это работает

1. Планировщик или `POST /api/parse` передаёт ссылки в `ParsingService`.
2. Каждая ссылка становится задачей `CompletableFuture` в кастомном пуле из 8 потоков.
3. Набор ссылок в работе не даёт обрабатывать одну страницу двум потокам одновременно, а семафор ограничивает число запросов к одному сайту (по умолчанию 4).
4. `PageFetcher` скачивает страницу, а `PageExtractor` достаёт данные, перебирая источники: JSON-LD (schema.org), мета-теги Open Graph, микроразметку и CSS-классы.
5. `ParseResultWriter` сохраняет результат короткой транзакцией. Ошибка одной ссылки не влияет на остальные и видна в `/api/urls`.
6. Запись о результате попадает в блокирующую очередь, откуда её забирает демон-поток и дописывает в файл. Потоки парсинга не ждут диск, а при остановке приложения очередь дописывается до конца.

Настройки находятся в `src/main/resources/application.yml`, секция `parser`.

## Ограничения

- База H2 хранится в памяти, поэтому после перезапуска её данные начинаются заново. Файл результатов при этом сохраняется и продолжает расти.
- Сайты с защитой от ботов или с ценой, которую подгружает JavaScript, могут вернуть ошибку или страницу без цены. В таком случае ссылка получит статус `FAILED`. Парсер проверен на [books.toscrape.com](https://books.toscrape.com) и [scrapingcourse.com](https://www.scrapingcourse.com/ecommerce/).
- Дата публикации есть не на всех страницах. Если её нет в разметке, берётся заголовок `Last-Modified`, и тогда `publishedAtSource` равен `HTTP_LAST_MODIFIED`. Если нет и его, поле равно `null`.
- `robots.txt` не проверяется.
- `/answer` читает все записи из БД и обрабатывает их в памяти, как того требует задание про `parallelStream`. При большой истории эффективнее сортировать и разбивать на страницы на уровне SQL.

## Тесты

```bash
./mvnw test
```
