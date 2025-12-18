package io.fundic.fundic_server;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@Component
public class CsvStockNameMapLoader {

    private final ResourceLoader resourceLoader;

    public CsvStockNameMapLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    // ✅ 너가 만든 최종 파일
    private static final String PATH = "classpath:data/stock_sector_map_en_fixed.csv";
    private static final Charset CSV_CHARSET = Charset.forName("UTF-8"); // utf-8-sig도 문제 없이 읽힘

    public Map<String, String> loadCodeToName() {
        Map<String, String> map = new HashMap<>();
        try {
            Resource resource = resourceLoader.getResource(PATH);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream(), CSV_CHARSET))) {
                String line;
                boolean first = true;
                while ((line = br.readLine()) != null) {
                    if (first) { first = false; continue; } // header skip

                    // code,name,sector_id,sector_name,market
                    String[] p = line.split(",", -1);
                    if (p.length < 2) continue;

                    String code = p[0].trim();
                    String name = p[1].trim();

                    if (!code.isEmpty() && !name.isEmpty()) {
                        map.put(code, name);
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("CSV code->name load failed: " + PATH, e);
        }
        return map;
    }
}
