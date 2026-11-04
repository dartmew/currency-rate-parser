package Univer.Parser.service;

import Univer.Parser.entity.CurrencyRate;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;


class CurrencyParsingServiceUnitTest {

    private CurrencyParsingService service;

    @BeforeEach
    void setUp() {
        service = new CurrencyParsingService(null, WebClient.builder(), null);
    }

    private Element parseValute(String innerXml) {
        String xml = "<Valute>" + innerXml + "</Valute>";
        return Jsoup.parse(xml, "", Parser.xmlParser()).selectFirst("Valute");
    }

    @Test
    void mapToCurrencyRate_parsesValidValute() {
        Element valute = parseValute(
                "<CharCode>USD</CharCode>" +
                "<Name>Доллар США</Name>" +
                "<Value>80,9734</Value>");

        CurrencyRate rate = service.mapToCurrencyRate(valute);

        assertNotNull(rate);
        assertEquals("USD", rate.getCurrencyCode());
        assertEquals("Доллар США", rate.getCurrencyName());
        assertEquals(80.9734, rate.getRate(), 0.0001);
        assertNotNull(rate.getDate());
    }

    @Test
    void mapToCurrencyRate_returnsNullOnInvalidValue() {
        Element valute = parseValute(
                "<CharCode>BAD</CharCode>" +
                "<Name>Некорректная валюта</Name>" +
                "<Value>not-a-number</Value>");

        CurrencyRate rate = service.mapToCurrencyRate(valute);

        assertNull(rate, "При невалидном Value метод должен вернуть null, а не бросить исключение");
    }
}