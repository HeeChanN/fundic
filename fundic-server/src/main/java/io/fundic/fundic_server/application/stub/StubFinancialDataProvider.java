package io.fundic.fundic_server.application.stub;

import io.fundic.fundic_server.application.FinancialDataProvider;
import io.fundic.fundic_server.domain.FinancialData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Stub 재무 데이터 제공자 (테스트용)
 * 실제 KIS API 연동 전까지 사용
 */
@Slf4j
@Component
@Profile({"local", "dev"})
public class StubFinancialDataProvider implements FinancialDataProvider {

    @Override
    public Map<String, FinancialData> getFinancialDataBatch(List<String> stockCodes) {
        Map<String, FinancialData> result = new HashMap<>();
        for (String stockCode : stockCodes) {
            result.put(stockCode, getFinancialData(stockCode));
        }
        return result;
    }

    @Override
    public FinancialData getFinancialData(String stockCode) {
        // 해시 기반 결정적인 랜덤 값 생성
        int seed = stockCode.hashCode();
        Random r = new Random(seed);

        return new FinancialData(
                stockCode,
                5.0 + r.nextDouble() * 25.0,    // PER: 5~30
                0.5 + r.nextDouble() * 3.5,     // PBR: 0.5~4.0
                3.0 + r.nextDouble() * 17.0,    // ROE: 3~20%
                5.0 + r.nextDouble() * 15.0,    // 영업이익률: 5~20%
                30.0 + r.nextDouble() * 120.0,  // 부채비율: 30~150%
                (long) (100000 + r.nextInt(9900000)), // 시가총액: 1000억~100조
                1000.0 + r.nextDouble() * 9000.0  // EPS: 1,000~10,000원
        );
    }
}
