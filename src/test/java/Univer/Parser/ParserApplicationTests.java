package Univer.Parser;

import Univer.Parser.controller.CurrencyController;
import Univer.Parser.entity.CurrencyRate;
import Univer.Parser.repository.CurrencyRateRepository;
import Univer.Parser.service.CurrencyParsingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CurrencyController.class)
class ParserApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrencyRateRepository repository;

    @MockitoBean
    private CurrencyParsingService parsingService;

    @Test
    void contextLoads() {
        // Spring-контекст контроллера поднимается
    }

    @Test
    void testGetAnswerEndpointReturns200() throws Exception {
        mockMvc.perform(get("/api/answer")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void testFetchEndpointReturnsCompletedStatus() throws Exception {
        when(parsingService.fetchAndSaveRates()).thenReturn(54);

        mockMvc.perform(post("/api/fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.savedRecords").value(54));
    }

    @Test
    void testGetRateByCodeAndDate() throws Exception {
        CurrencyRate mockRate = new CurrencyRate();
        mockRate.setCurrencyCode("USD");
        mockRate.setRate(80.97);

        when(repository.findByCurrencyCodeAndDate(eq("USD"), any(LocalDate.class)))
                .thenReturn(List.of(mockRate));

        mockMvc.perform(get("/api/rate/USD/2026-11-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].currencyCode").value("USD"));
    }

    @Test
    void testAnalyticsEndpointReturnsAggregates() throws Exception {
        CurrencyRate usd = new CurrencyRate();
        usd.setCurrencyCode("USD");
        usd.setRate(80.0);

        CurrencyRate eur = new CurrencyRate();
        eur.setCurrencyCode("EUR");
        eur.setRate(90.0);

        when(repository.findAll()).thenReturn(List.of(usd, eur));

        mockMvc.perform(get("/api/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecords").value(2))
                .andExpect(jsonPath("$.uniqueCurrencies").value(2));
    }
}
