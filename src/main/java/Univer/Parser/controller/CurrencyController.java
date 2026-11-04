package Univer.Parser.controller;

import Univer.Parser.entity.CurrencyRate;
import Univer.Parser.repository.CurrencyRateRepository;
import Univer.Parser.service.CurrencyParsingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "Курсы валют", description = "Парсер курсов ЦБ РФ")
public class CurrencyController {

    private final CurrencyRateRepository repository;
    private final CurrencyParsingService parsingService;
    private final Map<String, List<CurrencyRate>> cache = new ConcurrentHashMap<>();

    public CurrencyController(CurrencyRateRepository repository,
                              CurrencyParsingService parsingService) {
        this.repository = repository;
        this.parsingService = parsingService;
    }

    @Operation(summary = "Получить курсы", description = "Возвращает постраничный список курсов с сортировкой")
    @GetMapping("/answer")
    public Page<CurrencyRate> getAnswer(
            @Parameter(description = "Номер страницы (с 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Поле сортировки: rate, date, currencyCode, currencyName", example = "rate")
            @RequestParam(defaultValue = "date") String sortBy,
            @Parameter(description = "Направление сортировки: asc / desc", example = "desc")
            @RequestParam(defaultValue = "desc") String direction) {

        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        return repository.findAll(PageRequest.of(page, size, sort));
    }

    @Operation(
        summary = "Курс валюты на конкретную дату",
        description = "Возвращает курс указанной валюты (например, USD, EUR) на заданную дату. " +
                      "Дата в формате yyyy-MM-dd. Если данных за эту дату нет — вернётся пустой массив."
    )
    @GetMapping("/rate/{code}/{date}")
    public List<CurrencyRate> getRateByCodeAndDate(
            @Parameter(description = "Код валюты по ISO 4217", example = "USD")
            @PathVariable String code,
            @Parameter(description = "Дата в формате yyyy-MM-dd", example = "2026-11-02")
            @PathVariable String date) { // формат yyyy-MM-dd

        LocalDate localDate = LocalDate.parse(date);
        String cacheKey = code + "_" + date;

        return cache.computeIfAbsent(cacheKey, k ->
                repository.findByCurrencyCodeAndDate(code, localDate)
        );
    }

    @Operation(summary = "Ручной запуск парсинга", description = "Синхронно скачивает курсы и сохраняет в БД")
    @PostMapping("/fetch")
    public ResponseEntity<Map<String, Object>> fetchNow() {
        int saved = parsingService.fetchAndSaveRates();
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "savedRecords", saved
        ));
    }

    @Operation(
        summary = "Ручной запуск парсинга (асинхронно)",
        description = "Запускает парсинг в фоновом потоке из кастомного ExecutorService. " +
                      "Возвращает 202 Accepted сразу, результат сохраняется в БД через 1–3 секунды. " +
                      "Проверить результат можно через GET /api/answer."
    )
    @PostMapping("/fetch/async")
    public ResponseEntity<Map<String, Object>> fetchAsync() {
        CompletableFuture<Void> future = parsingService.fetchRatesAsync();

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                System.err.println("Async fetch failed: " + ex.getMessage());
            } else {
                System.out.println("Async fetch completed");
            }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "accepted",
                "message", "Parsing started in background. Check /api/answer in a few seconds."
        ));
    }

    @Operation(summary = "Запуск в простом потоке (Thread)", description = "Демонстрация создания и запуска обычного Thread")
    @GetMapping("/fetch/thread")
    public Map<String, Object> fetchInThread() {
        Runnable task = () -> {
            System.out.println("[" + Thread.currentThread().getName() + "] Старт парсинга в простом потоке");
            parsingService.fetchAndSaveRates();
            System.out.println("[" + Thread.currentThread().getName() + "] Парсинг завершен");
        };

        Thread thread = new Thread(task);
        thread.start();

        return Map.of(
                "status", "started",
                "threadName", thread.getName(),
                "message", "Парсинг запущен в отдельном Thread"
        );
    }

    @Operation(
        summary = "Аналитика по курсам (parallelStream)",
        description = "Извлекает все курсы из H2 и параллельно считает статистику через parallelStream"
    )
    @GetMapping("/analytics")
    public Map<String, Object> getAnalytics() {
        List<CurrencyRate> all = repository.findAll();

        if (all.isEmpty()) {
            return Map.of("message", "Нет данных. Сначала вызовите POST /api/fetch");
        }

        Map<String, Long> countAbove50 = all.parallelStream()
                .filter(r -> r.getRate() != null && r.getRate() > 50)
                .collect(Collectors.groupingByConcurrent(
                        CurrencyRate::getCurrencyCode,
                        Collectors.counting()));

        double totalRate = all.parallelStream()
                .filter(r -> r.getRate() != null)
                .mapToDouble(CurrencyRate::getRate)
                .sum();

        double averageRate = all.parallelStream()
                .filter(r -> r.getRate() != null)
                .mapToDouble(CurrencyRate::getRate)
                .average()
                .orElse(0.0);

        List<CurrencyRate> top3 = all.parallelStream()
                .filter(r -> r.getRate() != null)
                .sorted(Comparator.comparingDouble(CurrencyRate::getRate).reversed())
                .limit(3)
                .collect(Collectors.toList());

        Set<String> uniqueCurrencies = all.parallelStream()
                .map(CurrencyRate::getCurrencyCode)
                .collect(Collectors.toSet());

        return Map.of(
                "totalRecords", all.size(),
                "uniqueCurrencies", uniqueCurrencies.size(),
                "currenciesAbove50", countAbove50,
                "sumOfRates", totalRate,
                "averageRate", averageRate,
                "top3", top3
        );
    }
}
