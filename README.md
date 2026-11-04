# Currency Rate Parser — Итоговое ДЗ (Вариант 4)

Многопоточное Spring Boot-приложение для автоматизированного сбора, обработки и хранения актуальных курсов валют с официального зеркала ЦБ РФ.

---

## Инструкция по запуску

**Требования:** `JDK 17+` (проект протестирован на `JDK 21` и `Spring Boot 4.1.2`). `Maven` не требуется — используется wrapper `mvnw`.

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows (PowerShell / CMD)
mvnw.cmd spring-boot:run
```

## Как проверять (Пошаговый сценарий)
0. Запуск тестов
    ```bash
    ./mvnw clean test
    ```
    Ожидаемый результат: 
    ```bash
    BUILD SUCCESS, Tests run: 7, Failures: 0, Errors: 0.
    ```

1. Простые потоки: `Thread` и `Runnable`

    Запустите парсинг в обычном потоке:

    ```bash
    curl -X GET http://localhost:8080/api/fetch/thread
    ```
    В консоли приложения появятся логи:
    ```
    [Thread-XX] Старт парсинга в простом потоке
    [Thread-XX] Парсинг завершен
    ```

2. Блокирующие структуры и демон-потоки
    Запустите парсинг вручную (или подождите `@Scheduled`):

    ```bash
    curl -X POST http://localhost:8080/api/fetch
    ```
    В корне проекта появится файл `parser_history.log`. Внутри — логи с временем старта и завершения.
    Как это работает под капотом: метод `log()` кладет строку в `BlockingQueue`. Демон-поток в `LoggingService` забирает её через `take()` и пишет в файл в кодировке `UTF-8`.

3. `H2`, синхронизация и `Parallel Stream`
    Получение данных из `H2` с пагинацией и сортировкой:

    ```bash
    curl "http://localhost:8080/api/answer?page=0&size=5&sortBy=rate&direction=desc"
    ```
    Фильтрация и аналитика через `Parallel Stream` (извлечение из `H2`):

    ```bash
    curl http://localhost:8080/api/analytics
    ```
    Ожидаемый результат: `JSON` с суммой курсов, средним курсом, топ-3 валютами и количеством валют с курсом > 50. Все вычисления производятся через `all.parallelStream()`.

4. Асинхронность и `ExecutorService`

    Ручной запуск парсинга (синхронно):

    ```bash
    curl -X POST http://localhost:8080/api/fetch
    
    # → {"status":"completed","savedRecords":54}
    ```
    Асинхронный запуск в кастомном пуле потоков:

    ```bash
    curl -X POST http://localhost:8080/api/fetch/async
    # → HTTP 202 Accepted
    ```
    Парсинг уйдет в фон (в `newFixedThreadPool(5)`), в консоли через пару секунд появится `Async fetch completed`.

## Swagger UI (Интерактивная документация)

Откройте в браузере: http://localhost:8080/swagger-ui/index.html
Здесь можно нажать `Try it out` → `Execute` для любого эндпоинта и увидеть ответ прямо в браузере.
`OpenAPI-спека` доступна по адресу: http://localhost:8080/v3/api-docs.

## 🗄️ H2 Console (Проверка БД)

Откройте в браузере: http://localhost:8080/h2-console

* JDBC URL: `jdbc:h2:mem:currencydb`

* User Name: `sa`

* Password: *(пусто)*

* Запрос: `SELECT * FROM CURRENCY_RATES;`

## Архитектура и особенности реализации

```text
Univer.Parser/
├── config/SchedulingConfig.java        # @Scheduled(fixedRate = 3600000)
├── controller/CurrencyController.java  # REST-эндпоинты, Swagger
├── entity/CurrencyRate.java            # JPA-сущность (H2)
├── repository/CurrencyRateRepository.java
├── service/
│   ├── CurrencyParsingService.java     # Парсинг (WebClient), ExecutorService, synchronized
│   └── LoggingService.java             # BlockingQueue, демон-поток, запись в файл
└── ParserApplication.java
```
Ключевые решения:

* Защита от дубликатов (`Race Condition`): Метод `fetchAndSaveRates` помечен `synchronized`. При одновременном вызове из `@Scheduled` и HTTP-эндпоинта потоки выполняются по очереди, предотвращая дублирование записей в БД.

* Источник данных (`SSL`): Используется официальное зеркало ЦБ РФ https://www.cbr-xml-daily.ru/daily_utf8.xml. Прямой запрос к cbr.ru падает с ошибкой `PKIX path building failed (самоподписанный сертификат ЦБ не входит в truststore JVM)`. Зеркало отдает те же данные, но работает «из коробки» без ручной настройки сертификатов.

* Логирование: Запись в `parser_history.log` ведется асинхронно через `BlockingQueue` демон-потоком, чтобы не блокировать основной поток парсинга.
