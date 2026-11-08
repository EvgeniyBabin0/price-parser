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


**4. История цены.** Обход повторяется каждые 60 секунд, а цена в демо-магазине при каждом запросе немного меняется. Подождите минуту и откройте:

http://localhost:8080/answer?url=http://localhost:8080/demo-shop/products/1&latestOnly=false

**5. Состояние пула и список ссылок:**

- http://localhost:8080/api/status — потоки, очередь, счётчики и число запусков по расписанию
- http://localhost:8080/api/urls — все ссылки с итогом последней проверки

**6. База данных.** Откройте http://localhost:8080/h2-console и укажите JDBC URL `jdbc:h2:mem:pricedb`, пользователя `sa` и пустой пароль. Таблицы: `PRICE_RECORD` и `TRACKED_URL`.

**7. Файл результатов.** Демон-поток `result-file-writer` пишет каждый результат в `data/parse-results.jsonl` в папке запуска. Файл сохраняется после перезапуска, а последние записи можно посмотреть здесь:

http://localhost:8080/api/results-file?limit=20


## Тесты

```bash
./mvnw test
```
