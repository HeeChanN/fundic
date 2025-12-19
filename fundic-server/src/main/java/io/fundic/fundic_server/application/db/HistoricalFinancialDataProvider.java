package io.fundic.fundic_server.application.db;

import io.fundic.fundic_server.application.FinancialDataProvider;
import io.fundic.fundic_server.application.real.RealFinancialDataProvider;
import io.fundic.fundic_server.domain.FinancialData;
import io.fundic.fundic_server.domain.FinancialDataHistory;
import io.fundic.fundic_server.domain.Stock;
import io.fundic.fundic_server.infrastructure.sector.FinancialDataHistoryRepository;
import io.fundic.fundic_server.infrastructure.sector.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@Profile("!local")
@Primary
@RequiredArgsConstructor
public class HistoricalFinancialDataProvider implements FinancialDataProvider {

    private final FinancialDataHistoryRepository financialHistoryRepository;
    private final StockRepository stockRepository;
    private final RealFinancialDataProvider fallbackProvider;

    @Override
    public Map<String, FinancialData> getFinancialDataBatch(List<String> stockCodes) {
        log.debug("Fetching historical financial data for {} codes", stockCodes.size());

        List<FinancialDataHistory> latestData = financialHistoryRepository
                .findLatestByStockCodes(stockCodes);

        Map<String, FinancialData> result = new HashMap<>();

        for (FinancialDataHistory history : latestData) {
            FinancialData data = convertToFinancialData(history);
            result.put(history.getStock().getCode(), data);
        }

        // Fallback for missing data
        Set<String> missingCodes = stockCodes.stream()
                .filter(code -> !result.containsKey(code))
                .collect(Collectors.toSet());

        if (!missingCodes.isEmpty()) {
            log.warn("Missing financial data for {} stocks, using fallback", missingCodes.size());
            Map<String, FinancialData> fallbackData = fallbackProvider
                    .getFinancialDataBatch(new ArrayList<>(missingCodes));
            result.putAll(fallbackData);
        }

        return result;
    }

    @Override
    public FinancialData getFinancialData(String stockCode) {
        Stock stock = stockRepository.findByCode(stockCode).orElse(null);
        if (stock == null) {
            log.warn("Stock not found: {}", stockCode);
            return fallbackProvider.getFinancialData(stockCode);
        }

        Optional<FinancialDataHistory> latest = financialHistoryRepository
                .findLatestByStockId(stock.getId());

        if (latest.isEmpty()) {
            log.warn("No financial history for {}, using fallback", stockCode);
            return fallbackProvider.getFinancialData(stockCode);
        }

        return convertToFinancialData(latest.get());
    }

    private FinancialData convertToFinancialData(FinancialDataHistory history) {
        return new FinancialData(
                history.getStock().getCode(),
                history.getPer() != null ? history.getPer() : 0.0,
                history.getPbr() != null ? history.getPbr() : 0.0,
                history.getRoe() != null ? history.getRoe() : 0.0,
                history.getOperatingMargin() != null ? history.getOperatingMargin() : 0.0,
                history.getDebtRatio() != null ? history.getDebtRatio() : 0.0,
                history.getMarketCap() != null ? history.getMarketCap() : 0L,
                history.getEps() != null ? history.getEps() : 0.0
        );
    }
}
