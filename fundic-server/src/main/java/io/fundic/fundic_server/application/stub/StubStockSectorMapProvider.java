package io.fundic.fundic_server.application.stub;

import io.fundic.fundic_server.application.StockSectorMapProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Profile("local")
public class StubStockSectorMapProvider implements StockSectorMapProvider {

    @Override
    public Map<String, String> loadStockToSectorMap() {
        // TODO: CSV/DB로 교체
        return Map.of(
                "005930", "SEMICONDUCTOR",
                "000660", "SEMICONDUCTOR",
                "035420", "IT_SERVICE",
                "035720", "IT_SERVICE",
                "105560", "BANK"
        );
    }
}