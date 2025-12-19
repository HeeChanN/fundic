package io.fundic.fundic_server.application.real;

import io.fundic.fundic_server.application.StockDataProvider;
import io.fundic.fundic_server.domain.StockData;
import io.fundic.fundic_server.infrastructure.KisApiClient;
import io.fundic.fundic_server.infrastructure.dto.KisStockPriceResponse;
import io.fundic.fundic_server.infrastructure.dto.KisTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 실제 KIS API를 사용하는 주식 데이터 제공자
 * production 환경에서 사용
 */
@Slf4j
@Component
@Profile("local")
public class RealStockDataProvider implements StockDataProvider {

    private final KisApiClient kisApiClient;
    private String cachedAccessToken;

    public RealStockDataProvider(KisApiClient kisApiClient) {
        this.kisApiClient = kisApiClient;
    }

    @Override
    public Map<String, StockData> getStockDataBatch(List<String> stockCodes) {
        Map<String, StockData> result = new HashMap<>();

        // 토큰 발급 (배치 조회 시작 시 1회만)
        ensureAccessToken();

        for (String stockCode : stockCodes) {
            try {
                StockData stockData = getStockData(stockCode);
                if (stockData != null) {
                    result.put(stockCode, stockData);
                }
            } catch (Exception e) {
                log.error("Failed to fetch stock data for {}", stockCode, e);
            }
        }

        return result;
    }

    @Override
    public StockData getStockData(String stockCode) {
        try {
            ensureAccessToken();

            // 시장 구분 코드 결정 (첫 자리로 판단: 0~3 KOSPI, 그 외 KOSDAQ)
            String marketCode = determineMarketCode(stockCode);

            KisStockPriceResponse response = kisApiClient.fetchStockPrice(
                    cachedAccessToken,
                    stockCode,
                    marketCode
            );

            if (response == null || !response.isSuccess()) {
                log.warn("Failed to fetch stock price for {}: {}", stockCode,
                        response != null ? response.msg1() : "null response");
                return null;
            }

            return convertToStockData(stockCode, response);

        } catch (Exception e) {
            log.error("Error fetching stock data for {}", stockCode, e);
            return null;
        }
    }

    /**
     * 액세스 토큰 확보 (캐시된 토큰이 없으면 새로 발급)
     */
    private void ensureAccessToken() {
        if (cachedAccessToken == null) {
            KisTokenResponse tokenResponse = kisApiClient.issueAccessToken();
            if (tokenResponse != null) {
                cachedAccessToken = tokenResponse.accessToken();
                log.info("Access token issued successfully");
            } else {
                throw new RuntimeException("Failed to issue access token");
            }
        }
    }

    /**
     * 종목코드로부터 시장 구분 코드 결정
     * 0~3으로 시작: KOSPI (J)
     * 그 외: KOSDAQ (Q)
     */
    private String determineMarketCode(String stockCode) {
        if (stockCode == null || stockCode.length() != 6) {
            return "J"; // 기본값 KOSPI
        }

        char firstChar = stockCode.charAt(0);
        if (firstChar >= '0' && firstChar <= '3') {
            return "J"; // KOSPI
        } else {
            return "Q"; // KOSDAQ
        }
    }

    /**
     * KIS API 응답을 StockData 도메인 객체로 변환
     */
    private StockData convertToStockData(String stockCode, KisStockPriceResponse response) {
        KisStockPriceResponse.Output output = response.output();

        // 가격 정보 파싱
        int currentPrice = parseIntSafely(output.stckPrpr());
        long volume = parseLongSafely(output.acmlVol());
        double changeRate = parseDoubleSafely(output.prdyCtrt());

        // 기술적 지표는 별도 계산 필요 (현재는 기본값)
        // TODO: OHLCV 데이터를 가져와서 RSI, MACD 등 계산
        StockData.TechnicalIndicators technical = new StockData.TechnicalIndicators(
                50.0,  // RSI (기본값)
                0.0,   // MACD (기본값)
                (double) currentPrice,  // MA20 (현재가로 근사)
                (double) currentPrice   // MA60 (현재가로 근사)
        );

        return new StockData(
                stockCode,
                "종목" + stockCode,  // TODO: 종목명은 별도 API 또는 DB에서 조회
                currentPrice,
                volume,
                changeRate,
                technical
        );
    }

    private int parseIntSafely(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse int: {}", value);
            return 0;
        }
    }

    private long parseLongSafely(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse long: {}", value);
            return 0L;
        }
    }

    private double parseDoubleSafely(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse double: {}", value);
            return 0.0;
        }
    }
}
