package io.fundic.fundic_server.presentation;

import io.fundic.fundic_server.domain.Stock;
import io.fundic.fundic_server.domain.StockPriceHistory;
import io.fundic.fundic_server.infrastructure.sector.StockRepository;
import io.fundic.fundic_server.infrastructure.sector.StockPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 차트 데이터 제공 API
 */
@Slf4j
@RestController
@RequestMapping("/api/chart")
@RequiredArgsConstructor
public class ChartDataController {

    private final StockRepository stockRepository;
    private final StockPriceHistoryRepository priceHistoryRepository;

    /**
     * 종목의 캔들스틱 차트 데이터 조회
     * GET /api/chart/candlestick?stockCode=005930&days=60
     *
     * @param stockCode 종목 코드
     * @param days 조회할 일수 (기본 60일)
     * @return 캔들스틱 차트 데이터
     */
    @GetMapping("/candlestick")
    public ResponseEntity<?> getCandlestickData(
            @RequestParam String stockCode,
            @RequestParam(defaultValue = "60") int days) {

        try {
            // 종목 조회
            Stock stock = stockRepository.findByCode(stockCode)
                    .orElseThrow(() -> new RuntimeException("종목을 찾을 수 없습니다: " + stockCode));

            // 날짜 범위 계산
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(days);

            // 주가 데이터 조회
            List<StockPriceHistory> priceHistory = priceHistoryRepository
                    .findByStockAndTradeDateBetweenOrderByTradeDateAsc(stock, startDate, endDate);

            if (priceHistory.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "주가 데이터가 없습니다."
                ));
            }

            // Lightweight Charts 형식으로 변환
            List<Map<String, Object>> candlestickData = priceHistory.stream()
                    .map(price -> {
                        Map<String, Object> candle = new HashMap<>();
                        // Unix timestamp (초 단위)
                        candle.put("time", price.getTradeDate().atStartOfDay().toEpochSecond(ZoneOffset.UTC));
                        candle.put("open", price.getOpenPrice());
                        candle.put("high", price.getHighPrice());
                        candle.put("low", price.getLowPrice());
                        candle.put("close", price.getClosePrice());
                        return candle;
                    })
                    .collect(Collectors.toList());

            // 거래량 데이터
            List<Map<String, Object>> volumeData = priceHistory.stream()
                    .map(price -> {
                        Map<String, Object> vol = new HashMap<>();
                        vol.put("time", price.getTradeDate().atStartOfDay().toEpochSecond(ZoneOffset.UTC));
                        vol.put("value", price.getVolume());
                        // 상승(녹색)/하락(빨강) 색상 구분
                        vol.put("color", price.getClosePrice() >= price.getOpenPrice() ?
                                "rgba(38, 166, 154, 0.5)" : "rgba(239, 83, 80, 0.5)");
                        return vol;
                    })
                    .collect(Collectors.toList());

            // RSI 데이터
            List<Map<String, Object>> rsiData = priceHistory.stream()
                    .filter(price -> price.getRsi() != null)
                    .map(price -> {
                        Map<String, Object> rsi = new HashMap<>();
                        rsi.put("time", price.getTradeDate().atStartOfDay().toEpochSecond(ZoneOffset.UTC));
                        rsi.put("value", price.getRsi());
                        return rsi;
                    })
                    .collect(Collectors.toList());

            // 이동평균선 데이터
            List<Map<String, Object>> ma20Data = priceHistory.stream()
                    .filter(price -> price.getMa20() != null)
                    .map(price -> {
                        Map<String, Object> ma = new HashMap<>();
                        ma.put("time", price.getTradeDate().atStartOfDay().toEpochSecond(ZoneOffset.UTC));
                        ma.put("value", price.getMa20());
                        return ma;
                    })
                    .collect(Collectors.toList());

            List<Map<String, Object>> ma60Data = priceHistory.stream()
                    .filter(price -> price.getMa60() != null)
                    .map(price -> {
                        Map<String, Object> ma = new HashMap<>();
                        ma.put("time", price.getTradeDate().atStartOfDay().toEpochSecond(ZoneOffset.UTC));
                        ma.put("value", price.getMa60());
                        return ma;
                    })
                    .collect(Collectors.toList());

            // 종목 정보
            StockPriceHistory latest = priceHistory.get(priceHistory.size() - 1);
            Map<String, Object> stockInfo = new HashMap<>();
            stockInfo.put("name", stock.getName());
            stockInfo.put("code", stock.getCode());
            stockInfo.put("market", stock.getMarket());
            stockInfo.put("latestPrice", latest.getClosePrice());
            stockInfo.put("latestDate", latest.getTradeDate().toString());

            // 응답 데이터
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("stockInfo", stockInfo);
            response.put("candlestick", candlestickData);
            response.put("volume", volumeData);
            response.put("rsi", rsiData);
            response.put("ma20", ma20Data);
            response.put("ma60", ma60Data);
            response.put("dataPoints", priceHistory.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("차트 데이터 조회 실패: {}", stockCode, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}
