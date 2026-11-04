package Univer.Parser.repository;

import Univer.Parser.entity.CurrencyRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.time.LocalDate;
import java.util.List;

public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, Long>,
        JpaSpecificationExecutor<CurrencyRate> {
    List<CurrencyRate> findByCurrencyCodeAndDate(String currencyCode, LocalDate date);
    List<CurrencyRate> findByDate(LocalDate date);
}
