package Univer.Parser.service;

import Univer.Parser.entity.CurrencyRate;
import Univer.Parser.repository.CurrencyRateRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class CurrencyParsingService {

    private final CurrencyRateRepository repository;
    private final WebClient webClient;
    private final LoggingService loggingService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    public CurrencyParsingService(CurrencyRateRepository repository, WebClient.Builder webClientBuilder, LoggingService loggingService) {
        this.repository = repository;
        this.webClient = webClientBuilder.baseUrl("https://www.cbr-xml-daily.ru").build();
        this.loggingService = loggingService;
    }

    public synchronized int fetchAndSaveRates() {
        loggingService.log("Запуск парсинга ...");

        String xml = webClient.get()
                .uri("/daily_utf8.xml")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (xml == null) {
            return 0;
        }

        Document doc = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser());
        Elements valutes = doc.select("Valute");

        List<CurrencyRate> rates = valutes.parallelStream()
                .map(this::mapToCurrencyRate)
                .filter(rate -> rate != null)
                .collect(Collectors.toList());

        LocalDate today = LocalDate.now();
        List<CurrencyRate> existing = repository.findByDate(today);
        if (!existing.isEmpty()) {
            repository.deleteAll(existing);
        }

        repository.saveAll(rates);
        loggingService.log("Парсинг завершен. Сохранено записей: " + rates.size());
        return rates.size();
    }

    CurrencyRate mapToCurrencyRate(Element valute) {
        try {
            CurrencyRate rate = new CurrencyRate();
            rate.setCurrencyCode(valute.select("CharCode").text());
            rate.setCurrencyName(valute.select("Name").text());
            rate.setRate(Double.parseDouble(valute.select("Value").text().replace(",", ".")));
            rate.setDate(LocalDate.now());
            rate.setDailyChange(0.0);
            return rate;
        } catch (Exception e) {
            return null;
        }
    }

    public CompletableFuture<Void> fetchRatesAsync() {
        return CompletableFuture.runAsync(this::fetchAndSaveRates, executorService);
    }
}