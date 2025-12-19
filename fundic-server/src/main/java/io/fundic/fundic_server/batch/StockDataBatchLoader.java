package io.fundic.fundic_server.batch;

import io.fundic.fundic_server.domain.FinancialDataHistory;
import io.fundic.fundic_server.domain.Stock;
import io.fundic.fundic_server.domain.StockPriceHistory;
import io.fundic.fundic_server.infrastructure.KisApiClient;
import io.fundic.fundic_server.infrastructure.dto.KisFinancialResponse;
import io.fundic.fundic_server.infrastructure.dto.KisStockPriceResponse;
import io.fundic.fundic_server.infrastructure.dto.KisTokenResponse;
import io.fundic.fundic_server.infrastructure.sector.FinancialDataHistoryRepository;
import io.fundic.fundic_server.infrastructure.sector.StockPriceHistoryRepository;
import io.fundic.fundic_server.infrastructure.sector.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockDataBatchLoader {

    private final StockRepository stockRepository;
    private final StockPriceHistoryRepository priceHistoryRepository;
    private final FinancialDataHistoryRepository financialHistoryRepository;
    private final KisApiClient kisApiClient;
    private final TechnicalIndicatorCalculator technicalCalculator;
    private final RateLimiter rateLimiter;

    private String cachedAccessToken;
    private LocalDateTime tokenExpiryTime;

    /**
     * Daily job: Load previous trading day's closing data
     * Runs at 6 PM KST (after market close at 3:30 PM + settlement)
     */
    @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Seoul")
    public void loadDailyStockPrices() {
        log.info("Starting daily stock price batch load");

        try {
            LocalDate targetDate = LocalDate.now().minusDays(1);  // Previous trading day

            // Skip if already loaded
            if (isDataLoadedForDate(targetDate)) {
                log.info("Data already loaded for {}, skipping", targetDate);
                return;
            }

            List<Stock> allStocks = stockRepository.findAll();
            log.info("Loading prices for {} stocks", allStocks.size());

            ensureAccessToken();

            int successCount = 0;
            int failCount = 0;

            for (Stock stock : allStocks) {
                try {
                    // Rate limiting 적용
                    rateLimiter.acquirePermit();

                    loadStockPriceData(stock, targetDate);
                    successCount++;

                    // 100개마다 진행 상황 로그
                    if (successCount % 100 == 0) {
                        log.info("Daily load progress: {}/{} stocks completed",
                            successCount, allStocks.size());
                    }

                } catch (Exception e) {
                    log.error("Failed to load price for {}: {}", stock.getCode(), e.getMessage());
                    failCount++;
                }
            }

            log.info("Daily load completed: {} success, {} failed", successCount, failCount);

        } catch (Exception e) {
            log.error("Daily batch load failed", e);
        }
    }

    /**
     * Quarterly job: Load financial data
     * Runs on the 15th of each quarter's first month at 7 PM
     */
    @Scheduled(cron = "0 0 19 15 1,4,7,10 *", zone = "Asia/Seoul")
    public void loadQuarterlyFinancialData() {
        log.info("Starting quarterly financial data batch load");

        try {
            List<Stock> allStocks = stockRepository.findAll();
            ensureAccessToken();

            // Determine current fiscal quarter
            LocalDate now = LocalDate.now();
            int fiscalYear = now.getYear();
            int fiscalQuarter = (now.getMonthValue() - 1) / 3 + 1;

            int successCount = 0;
            int failCount = 0;

            for (Stock stock : allStocks) {
                try {
                    // Check if already loaded
                    if (financialHistoryRepository.existsByStockAndFiscalYearAndFiscalQuarter(
                            stock, fiscalYear, fiscalQuarter)) {
                        continue;
                    }

                    // Rate limiting 적용
                    rateLimiter.acquirePermit();

                    loadFinancialData(stock, fiscalYear, fiscalQuarter);
                    successCount++;

                    if (successCount % 100 == 0) {
                        log.info("Quarterly load progress: {}/{} stocks completed",
                            successCount, allStocks.size());
                    }

                } catch (Exception e) {
                    log.error("Failed to load financial data for {}: {}",
                            stock.getCode(), e.getMessage());
                    failCount++;
                }
            }

            log.info("Quarterly load completed: {} success, {} failed", successCount, failCount);

        } catch (Exception e) {
            log.error("Quarterly batch load failed", e);
        }
    }

    /**
     * 백필 작업 - 배치 분할 지원
     *
     * @param daysBack 백필할 일수
     */
    public void backfillHistoricalData(int daysBack) {
        backfillHistoricalData(daysBack, -1);
    }

    /**
     * 백필 작업 - 일일 처리량 제한
     *
     * @param daysBack 백필할 총 일수
     * @param maxDaysPerRun 1회 실행당 최대 처리 일수 (-1이면 제한 없음)
     */
    public void backfillHistoricalData(int daysBack, int maxDaysPerRun) {
        log.info("Starting backfill for {} days (maxDaysPerRun: {})", daysBack, maxDaysPerRun);

        List<Stock> allStocks = stockRepository.findAll();
        ensureAccessToken();

        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(daysBack);

        int daysProcessed = 0;
        int totalStocksProcessed = 0;
        int totalFailures = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            // 일일 처리량 제한 체크
            if (maxDaysPerRun > 0 && daysProcessed >= maxDaysPerRun) {
                log.info("Reached daily limit ({} days), stopping. Resume with: backfill from {}",
                    maxDaysPerRun, date);
                break;
            }

            // Skip weekends
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY ||
                    date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }

            final LocalDate targetDate = date;
            log.info("Backfilling data for {} ({} stocks)", targetDate, allStocks.size());

            int dailySuccess = 0;
            int dailyFail = 0;

            for (Stock stock : allStocks) {
                try {
                    // 이미 데이터가 있으면 스킵
                    if (priceHistoryRepository.existsByStockAndTradeDate(stock, targetDate)) {
                        continue;
                    }

                    // 토큰 만료 확인 및 갱신
                    ensureAccessToken();

                    // Rate limiting 적용
                    rateLimiter.acquirePermit();

                    loadStockPriceData(stock, targetDate);
                    dailySuccess++;
                    totalStocksProcessed++;

                    // 100개마다 진행 상황 로그
                    if (totalStocksProcessed % 100 == 0) {
                        log.info("Backfill progress: {} stocks processed, Rate limit: {}",
                            totalStocksProcessed, rateLimiter.getStats());
                    }

                } catch (Exception e) {
                    log.error("Backfill failed for {} on {}: {}",
                            stock.getCode(), targetDate, e.getMessage());
                    dailyFail++;
                    totalFailures++;

                    // 연속 실패가 많으면 중단
                    if (dailyFail > 50) {
                        log.error("Too many failures ({}) for date {}, skipping remaining stocks",
                            dailyFail, targetDate);
                        break;
                    }
                }
            }

            daysProcessed++;
            log.info("Completed {} - Success: {}, Failed: {}",
                targetDate, dailySuccess, dailyFail);
        }

        log.info("Backfill completed - Days: {}, Total stocks: {}, Failures: {}",
            daysProcessed, totalStocksProcessed, totalFailures);
    }

    /**
     * 특정 날짜부터 백필 (재개 기능)
     */
    public void backfillFromDate(LocalDate startDate, int days) {
        log.info("Starting backfill from {} for {} days", startDate, days);

        List<Stock> allStocks = stockRepository.findAll();
        ensureAccessToken();

        LocalDate endDate = startDate.plusDays(days);
        int totalStocksProcessed = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY ||
                    date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }

            final LocalDate targetDate = date;
            log.info("Backfilling data for {}", targetDate);

            for (Stock stock : allStocks) {
                try {
                    if (!priceHistoryRepository.existsByStockAndTradeDate(stock, targetDate)) {
                        ensureAccessToken();
                        rateLimiter.acquirePermit();
                        loadStockPriceData(stock, targetDate);
                        totalStocksProcessed++;

                        if (totalStocksProcessed % 100 == 0) {
                            log.info("Progress: {} stocks processed", totalStocksProcessed);
                        }
                    }
                } catch (Exception e) {
                    log.error("Backfill failed for {} on {}: {}",
                            stock.getCode(), targetDate, e.getMessage());
                }
            }
        }

        log.info("Backfill from date completed: {} stocks processed", totalStocksProcessed);
    }

    private void loadStockPriceData(Stock stock, LocalDate tradeDate) {
        String marketCode = determineMarketCode(stock.getCode());

        KisStockPriceResponse response = kisApiClient.fetchStockPrice(
                cachedAccessToken,
                stock.getCode(),
                marketCode
        );

        if (response == null || !response.isSuccess()) {
            throw new RuntimeException("KIS API returned error: " +
                    (response != null ? response.msg1() : "null"));
        }

        // Parse response
        KisStockPriceResponse.Output output = response.output();

        StockPriceHistory history = StockPriceHistory.of(
                stock,
                tradeDate,
                parseIntSafely(output.stckOprc()),
                parseIntSafely(output.stckHgpr()),
                parseIntSafely(output.stckLwpr()),
                parseIntSafely(output.stckPrpr()),
                parseLongSafely(output.acmlVol()),
                parseDoubleSafely(output.prdyCtrt()),
                parseIntSafely(output.stckPrpr())
        );

        // Calculate technical indicators
        calculateAndSetTechnicalIndicators(stock, history);

        priceHistoryRepository.save(history);
    }

    private void loadFinancialData(Stock stock, int fiscalYear, int fiscalQuarter) {
        KisFinancialResponse response = kisApiClient.fetchFinancialInfo(
                cachedAccessToken,
                stock.getCode()
        );

        if (response == null || !response.isSuccess()) {
            throw new RuntimeException("KIS API returned error");
        }

        KisFinancialResponse.Output output = response.output();

        FinancialDataHistory history = FinancialDataHistory.of(
                stock,
                fiscalYear,
                fiscalQuarter,
                parseDoubleSafely(output.per()),
                parseDoubleSafely(output.pbr()),
                parseDoubleSafely(output.roe()),
                parseDoubleSafely(output.saleOtrt()),
                parseDoubleSafely(output.debtRate()),
                0L,
                parseDoubleSafely(output.eps()),
                LocalDate.now()
        );

        financialHistoryRepository.save(history);
    }

    private void calculateAndSetTechnicalIndicators(Stock stock, StockPriceHistory currentHistory) {
        List<StockPriceHistory> historicalData = priceHistoryRepository
                .findTopNByStockIdOrderByTradeDateDesc(stock.getId(), 60);

        List<Integer> closePrices = new ArrayList<>();
        closePrices.add(currentHistory.getClosePrice());
        for (StockPriceHistory h : historicalData) {
            closePrices.add(h.getClosePrice());
        }

        TechnicalIndicatorCalculator.TechnicalIndicators indicators =
                technicalCalculator.calculate(closePrices);

        currentHistory.setRsi(indicators.rsi());
        currentHistory.setMacd(indicators.macd());
        currentHistory.setMa20(indicators.ma20());
        currentHistory.setMa60(indicators.ma60());
    }

    private boolean isDataLoadedForDate(LocalDate date) {
        List<Stock> stocks = stockRepository.findAll();
        if (stocks.isEmpty()) {
            return false;
        }
        return priceHistoryRepository.existsByStockAndTradeDate(stocks.get(0), date);
    }

    private void ensureAccessToken() {
        // 토큰이 없거나 만료 예정이면 갱신
        if (cachedAccessToken == null || isTokenExpiringSoon()) {
            KisTokenResponse tokenResponse = kisApiClient.issueAccessToken();
            if (tokenResponse != null) {
                cachedAccessToken = tokenResponse.accessToken();
                // 토큰 만료 시간 설정 (일반적으로 24시간, 여유있게 23시간으로)
                tokenExpiryTime = LocalDateTime.now().plusHours(23);
                log.info("Access token refreshed, expires at: {}", tokenExpiryTime);
            } else {
                throw new RuntimeException("Failed to issue access token");
            }
        }
    }

    private boolean isTokenExpiringSoon() {
        if (tokenExpiryTime == null) {
            return true;
        }
        // 1시간 전에 갱신
        return LocalDateTime.now().plusHours(1).isAfter(tokenExpiryTime);
    }

    private String determineMarketCode(String stockCode) {
        if (stockCode == null || stockCode.length() < 1) {
            return "J";
        }
        char firstChar = stockCode.charAt(0);
        return (firstChar >= '0' && firstChar <= '3') ? "J" : "Q";
    }

    private int parseIntSafely(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseLongSafely(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }

    private double parseDoubleSafely(String value) {
        if (value == null || value.isBlank()) return 0.0;
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
