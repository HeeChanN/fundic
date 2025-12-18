package io.fundic.fundic_server.application.stub;

import io.fundic.fundic_server.application.StockScoreProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class StubStockScoreProvider implements StockScoreProvider {

    @Override
    public Map<String, Integer> scoreStocks(Iterable<String> stockCodes) {
        Map<String, Integer> m = new HashMap<>();
        for (String code : stockCodes) {
            // 재현 가능한 더미 점수(코드 해시 기반)
            int h = Math.abs(code.hashCode());
            int score = 50 + (h % 51); // 50~100
            m.put(code, score);
        }
        return m;
    }
}
