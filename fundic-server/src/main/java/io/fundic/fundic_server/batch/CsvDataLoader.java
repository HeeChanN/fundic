package io.fundic.fundic_server.batch;

import io.fundic.fundic_server.domain.Stock;
import io.fundic.fundic_server.domain.StockPriceHistory;
import io.fundic.fundic_server.infrastructure.sector.StockPriceHistoryRepository;
import io.fundic.fundic_server.infrastructure.sector.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * CSV 파일에서 주가 데이터를 읽어 DB에 적재
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CsvDataLoader {

    private final StockRepository stockRepository;
    private final StockPriceHistoryRepository priceHistoryRepository;
    private final TechnicalIndicatorCalculator technicalCalculator;

    /**
     * CSV 파일에서 데이터 로드
     *
     * @param csvFilePath CSV 파일 경로
     */
    @Transactional
    public void loadFromCsv(String csvFilePath) {
        log.info("CSV 파일 로딩 시작: {}", csvFilePath);

        // 모든 종목 조회 (stock_code -> Stock 매핑)
        Map<String, Stock> stockMap = new HashMap<>();
        List<Stock> allStocks = stockRepository.findAll();
        for (Stock stock : allStocks) {
            stockMap.put(stock.getCode(), stock);
        }

        log.info("DB에서 {}개 종목 로드됨", stockMap.size());

        int totalLines = 0;
        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;

        List<StockPriceHistory> batch = new ArrayList<>();
        int batchSize = 500;

        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            String header = br.readLine(); // 헤더 스킵
            log.info("CSV 헤더: {}", header);

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            while ((line = br.readLine()) != null) {
                totalLines++;

                try {
                    String[] fields = line.split(",");
                    if (fields.length < 8) {
                        log.warn("잘못된 형식 (라인 {}): {}", totalLines, line);
                        errorCount++;
                        continue;
                    }

                    // CSV 형식: stock_code, trade_date, open_price, high_price, low_price, close_price, volume, change_rate
                    String stockCode = fields[0].trim();
                    LocalDate tradeDate = LocalDate.parse(fields[1].trim(), dateFormatter);
                    int openPrice = parseIntSafely(fields[2]);
                    int highPrice = parseIntSafely(fields[3]);
                    int lowPrice = parseIntSafely(fields[4]);
                    int closePrice = parseIntSafely(fields[5]);
                    long volume = parseLongSafely(fields[6]);
                    double changeRate = parseDoubleSafely(fields[7]);

                    // 종목 찾기
                    Stock stock = stockMap.get(stockCode);
                    if (stock == null) {
                        if (skipCount == 0) {
                            log.warn("종목 코드 {}는 DB에 없습니다. (이후 동일 에러는 로그 생략)", stockCode);
                        }
                        skipCount++;
                        continue;
                    }

                    // 이미 존재하는 데이터면 스킵
                    if (priceHistoryRepository.existsByStockAndTradeDate(stock, tradeDate)) {
                        skipCount++;
                        continue;
                    }

                    // StockPriceHistory 생성
                    StockPriceHistory history = StockPriceHistory.of(
                            stock,
                            tradeDate,
                            openPrice,
                            highPrice,
                            lowPrice,
                            closePrice,
                            volume,
                            changeRate,
                            closePrice
                    );

                    batch.add(history);
                    successCount++;

                    // 배치 저장
                    if (batch.size() >= batchSize) {
                        priceHistoryRepository.saveAll(batch);
                        log.info("진행: {} / {} 레코드 저장됨 (성공: {}, 스킵: {}, 에러: {})",
                                totalLines, totalLines, successCount, skipCount, errorCount);
                        batch.clear();
                    }

                } catch (Exception e) {
                    log.error("라인 {} 처리 실패: {} - {}", totalLines, line, e.getMessage());
                    errorCount++;
                }
            }

            // 남은 배치 저장
            if (!batch.isEmpty()) {
                priceHistoryRepository.saveAll(batch);
                batch.clear();
            }

            log.info("CSV 로딩 완료!");
            log.info("총 라인: {}", totalLines);
            log.info("성공: {}", successCount);
            log.info("스킵: {} (중복 또는 미등록 종목)", skipCount);
            log.info("에러: {}", errorCount);

        } catch (Exception e) {
            log.error("CSV 파일 읽기 실패", e);
            throw new RuntimeException("CSV 로딩 실패", e);
        }

        // 기술적 지표 계산
        log.info("기술적 지표 계산 시작...");
        calculateTechnicalIndicatorsForAll();
    }

    /**
     * 모든 종목의 기술적 지표 계산
     */
    private void calculateTechnicalIndicatorsForAll() {
        List<Stock> allStocks = stockRepository.findAll();
        int count = 0;

        for (Stock stock : allStocks) {
            try {
                List<StockPriceHistory> histories = priceHistoryRepository
                        .findTopNByStockIdOrderByTradeDateDesc(stock.getId(), 200);

                if (histories.isEmpty()) {
                    continue;
                }

                // 최신 데이터부터 처리 (날짜 역순)
                for (int i = histories.size() - 1; i >= 0; i--) {
                    StockPriceHistory current = histories.get(i);

                    // 이 시점까지의 과거 데이터 수집
                    List<Integer> closePrices = new ArrayList<>();
                    closePrices.add(current.getClosePrice());

                    for (int j = i - 1; j >= Math.max(0, i - 60); j--) {
                        closePrices.add(histories.get(j).getClosePrice());
                    }

                    // 지표 계산
                    TechnicalIndicatorCalculator.TechnicalIndicators indicators =
                            technicalCalculator.calculate(closePrices);

                    current.setRsi(indicators.rsi());
                    current.setMacd(indicators.macd());
                    current.setMa20(indicators.ma20());
                    current.setMa60(indicators.ma60());
                }

                // 배치 저장
                priceHistoryRepository.saveAll(histories);
                count++;

                if (count % 100 == 0) {
                    log.info("기술적 지표 계산 진행: {}/{} 종목 완료", count, allStocks.size());
                }

            } catch (Exception e) {
                log.error("종목 {} 지표 계산 실패: {}", stock.getCode(), e.getMessage());
            }
        }

        log.info("기술적 지표 계산 완료: {} 종목", count);
    }

    private int parseIntSafely(String value) {
        try {
            return (int) Double.parseDouble(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseLongSafely(String value) {
        try {
            return (long) Double.parseDouble(value.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private double parseDoubleSafely(String value) {
        if (value == null || value.trim().isEmpty()) return 0.0;
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}
