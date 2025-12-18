package io.fundic.fundic_server.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Profile("dev")
public class CsvStockSectorMapProvider implements StockSectorMapProvider {

    private final ResourceLoader resourceLoader;

    // ✅ classpath:./data/ 아래
    private static final String PATH = "classpath:data/stock_sector_map.csv";

    // ✅ BOM 포함 UTF-8도 안전하게 처리
    private static final Charset CSV_CHARSET = Charset.forName("UTF-8");

    @Override
    public Map<String, String> loadStockToSectorMap() {
        Map<String, String> map = new HashMap<>();
        try {
            Resource resource = resourceLoader.getResource(PATH);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream(), CSV_CHARSET))) {
                String line;
                boolean first = true;

                while ((line = br.readLine()) != null) {
                    if (first) { // header skip
                        first = false;
                        continue;
                    }
                    // code,name,sector_id,sector_name,market
                    String[] p = line.split(",", -1);
                    if (p.length < 3) continue;

                    String code = p[0].trim();
                    String sectorId = p[2].trim();

                    if (!code.isEmpty() && !sectorId.isEmpty()) {
                        map.put(code, sectorId);
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("CSV stock-sector map load failed: " + PATH, e);
        }
        return map;
    }
}