package io.fundic.fundic_server.application.db;

import io.fundic.fundic_server.application.StockDataProvider;
import io.fundic.fundic_server.application.real.RealStockDataProvider;
import io.fundic.fundic_server.domain.Stock;
import io.fundic.fundic_server.domain.StockData;
import io.fundic.fundic_server.domain.StockPriceHistory;
import io.fundic.fundic_server.infrastructure.sector.StockPriceHistoryRepository;
import io.fundic.fundic_server.infrastructure.sector.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@Profile("!local")
@Primary
@RequiredArgsConstructor
public class HistoricalStockDataProvider implements StockDataProvider {

    private final StockPriceHistoryRepository priceHistoryRepository;
    private final StockRepository stockRepository;
    private final RealStockDataProvider fallbackProvider;

    @Override
    public Map<String, StockData> getStockDataBatch(List<String> stockCodes) {
        log.debug("Fetching historical stock data for {} codes", stockCodes.size());

        // Batch query for latest prices
        List<StockPriceHistory> latestPrices = priceHistoryRepository
                .findLatestByStockCodes(stockCodes);

        Map<String, StockData> result = new HashMap<>();

        for (StockPriceHistory history : latestPrices) {
            StockData stockData = convertToStockData(history);
            result.put(history.getStock().getCode(), stockData);
        }

        // Fallback for missing data
        Set<String> missingCodes = stockCodes.stream()
                .filter(code -> !result.containsKey(code))
                .collect(Collectors.toSet());

        if (!missingCodes.isEmpty()) {
            log.warn("Missing DB data for {} stocks, using fallback", missingCodes.size());
            Map<String, StockData> fallbackData = fallbackProvider
                    .getStockDataBatch(new ArrayList<>(missingCodes));
            result.putAll(fallbackData);
        }

        return result;
    }

    @Override
    public StockData getStockData(String stockCode) {
        Stock stock = stockRepository.findByCode(stockCode)
                .orElse(null);

        if (stock == null) {
            log.warn("Stock not found: {}", stockCode);
            return fallbackProvider.getStockData(stockCode);
        }

        Optional<StockPriceHistory> latest = priceHistoryRepository
                .findFirstByStockOrderByTradeDateDesc(stock);

        if (latest.isEmpty()) {
            log.warn("No price history for {}, using fallback", stockCode);
            return fallbackProvider.getStockData(stockCode);
        }

        return convertToStockData(latest.get());
    }

    /**
     * NEW METHOD: Get time-series data for a specific period
     */
    public Map<String, List<StockData>> getStockDataTimeSeries(
            List<String> stockCodes,
            LocalDate startDate,
            LocalDate endDate) {

        log.debug("Fetching time-series data for {} stocks from {} to {}",
                stockCodes.size(), startDate, endDate);

        Map<String, List<StockData>> result = new HashMap<>();

        for (String stockCode : stockCodes) {
            Stock stock = stockRepository.findByCode(stockCode).orElse(null);
            if (stock == null) continue;

            List<StockPriceHistory> history = priceHistoryRepository
                    .findByStockIdAndDateRange(stock.getId(), startDate, endDate);

            List<StockData> timeSeries = history.stream()
                    .map(this::convertToStockData)
                    .toList();

            result.put(stockCode, timeSeries);
        }

        return result;
    }

    /**
     * NEW METHOD: Get last N days of data
     */
    public Map<String, List<StockData>> getStockDataLastNDays(
            List<String> stockCodes,
            int days) {

        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(days);

        return getStockDataTimeSeries(stockCodes, startDate, endDate);
    }

    private StockData convertToStockData(StockPriceHistory history) {
        StockData.TechnicalIndicators technical = new StockData.TechnicalIndicators(
                history.getRsi() != null ? history.getRsi() : 50.0,
                history.getMacd() != null ? history.getMacd() : 0.0,
                history.getMa20() != null ? history.getMa20() : history.getClosePrice().doubleValue(),
                history.getMa60() != null ? history.getMa60() : history.getClosePrice().doubleValue()
        );

        return new StockData(
                history.getStock().getCode(),
                history.getStock().getName(),
                history.getClosePrice(),
                history.getVolume(),
                history.getChangeRate(),
                technical
        );
    }
}
