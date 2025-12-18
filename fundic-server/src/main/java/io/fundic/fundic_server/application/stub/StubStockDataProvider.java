package io.fundic.fundic_server.application.stub;

import io.fundic.fundic_server.application.StockDataProvider;
import io.fundic.fundic_server.domain.StockData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Stub 종목 데이터 제공자 (테스트용)
 * 실제 KIS API 연동 전까지 사용
 */
@Slf4j
@Component
@Profile({"local", "dev"})
public class StubStockDataProvider implements StockDataProvider {

    private final Random random = new Random();

    @Override
    public Map<String, StockData> getStockDataBatch(List<String> stockCodes) {
        Map<String, StockData> result = new HashMap<>();
        for (String stockCode : stockCodes) {
            result.put(stockCode, getStockData(stockCode));
        }
        return result;
    }

    @Override
    public StockData getStockData(String stockCode) {
        // 해시 기반 결정적인 랜덤 값 생성 (같은 종목 코드는 같은 값 반환)
        int seed = stockCode.hashCode();
        Random r = new Random(seed);

        int basePrice = 10000 + r.nextInt(90000); // 10,000 ~ 100,000원
        long volume = (long) (100000 + r.nextInt(9900000)); // 10만 ~ 1000만주
        double changeRate = -5.0 + r.nextDouble() * 10.0; // -5% ~ +5%

        StockData.TechnicalIndicators technical = new StockData.TechnicalIndicators(
                30.0 + r.nextDouble() * 40.0,  // RSI: 30~70
                r.nextDouble() * 1000 - 500,   // MACD: -500~500
                (double) (basePrice * (0.95 + r.nextDouble() * 0.1)), // MA20: 가격 ±5%
                (double) (basePrice * (0.90 + r.nextDouble() * 0.2))  // MA60: 가격 ±10%
        );

        return new StockData(
                stockCode,
                "종목" + stockCode, // 실제로는 종목명 조회 필요
                basePrice,
                volume,
                changeRate,
                technical
        );
    }
}
