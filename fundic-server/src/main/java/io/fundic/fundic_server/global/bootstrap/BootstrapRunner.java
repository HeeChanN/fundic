package io.fundic.fundic_server.global.bootstrap;

import io.fundic.fundic_server.domain.Market;
import io.fundic.fundic_server.domain.Sector;
import io.fundic.fundic_server.domain.Stock;
import io.fundic.fundic_server.infrastructure.sector.SectorRepository;
import io.fundic.fundic_server.infrastructure.sector.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("dev")
public class BootstrapRunner implements ApplicationRunner {

    private static final String DEFAULT_SECTOR = "미분류";

    private final BootstrapProperties props;
    private final SectorRepository sectorRepository;
    private final StockRepository stockRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (!props.enabled()) {
            log.info("[BOOTSTRAP] disabled");
            return;
        }

        if (props.clearExisting()) {
            log.info("[BOOTSTRAP] clear existing data");
            stockRepository.deleteAllInBatch();
            sectorRepository.deleteAllInBatch();
        }

        Map<String, Sector> sectorByName = new HashMap<>();
        Sector unknown = sectorRepository.findByName(DEFAULT_SECTOR)
                .orElseGet(() -> sectorRepository.save(Sector.of(DEFAULT_SECTOR)));
        sectorByName.put(DEFAULT_SECTOR, unknown);

        // 1) Sector CSV 여러 개 → 업종명 중복 제거 적재
        for (Resource sectorCsv : props.sectorCsvs()) {
            loadSectorsFromSectorCsv(sectorCsv, sectorByName);
        }
        log.info("[BOOTSTRAP] sectors loaded: {}", sectorByName.size());

        // 2) Stock CSV 여러 개 → 종목 적재 + sector 매핑
        for (Resource stockCsv : props.stockCsvs()) {
            loadStocks(stockCsv, sectorByName, unknown);
        }
        log.info("[BOOTSTRAP] stocks loaded: {}", stockRepository.count());
    }

    private void loadSectorsFromSectorCsv(Resource sectorCsv,
                                          Map<String, Sector> sectorByName) throws Exception {
        if (sectorCsv == null || !sectorCsv.exists()) {
            log.warn("[BOOTSTRAP] sector csv not found");
            return;
        }

        Charset cs = Charset.forName(props.encoding());
        log.info("[BOOTSTRAP] load sector csv: {}", sectorCsv.getDescription());

        try (BufferedReader br = new BufferedReader(new InputStreamReader(sectorCsv.getInputStream(), cs))) {
            String header = br.readLine();
            if (header == null) return;

            List<String> headers = CsvLineParser.parse(header);
            int industryIdx = indexOf(headers, "업종명");
            if (industryIdx < 0) industryIdx = 0; // 업종명이 첫 컬럼인 경우 대응

            String line;
            while ((line = br.readLine()) != null) {
                List<String> cols = CsvLineParser.parse(line);
                if (cols.size() <= industryIdx) continue;

                String industryName = normalize(cols.get(industryIdx));
                if (industryName.isBlank()) continue;

                sectorByName.computeIfAbsent(industryName,
                        k -> sectorRepository.save(Sector.of(k)));
            }
        }
    }

    private void loadStocks(Resource stockCsv,
                            Map<String, Sector> sectorByName,
                            Sector unknown) throws Exception {

        if (stockCsv == null || !stockCsv.exists()) {
            log.warn("[BOOTSTRAP] stock csv not found");
            return;
        }

        Charset cs = Charset.forName(props.encoding());
        log.info("[BOOTSTRAP] load stock csv: {}", stockCsv.getDescription());

        try (BufferedReader br = new BufferedReader(new InputStreamReader(stockCsv.getInputStream(), cs))) {
            String header = br.readLine();
            if (header == null) return;

            List<String> h = CsvLineParser.parse(header);

            int codeIdx = indexOf(h, "종목코드");
            int nameIdx = indexOf(h, "종목명");
            int marketIdx = indexOf(h, "시장구분");
            int industryIdx = indexOf(h, "업종명");
            int closeIdx = indexOf(h, "종가");
            int diffIdx = indexOf(h, "대비");
            int rateIdx = indexOf(h, "등락률");
            int capIdx = indexOf(h, "시가총액");

            if (codeIdx < 0 || nameIdx < 0 || marketIdx < 0 || industryIdx < 0) {
                log.warn("[BOOTSTRAP] missing required headers. file will be skipped.");
                return;
            }

            List<Stock> batch = new ArrayList<>(500);
            String line;

            while ((line = br.readLine()) != null) {
                List<String> cols = CsvLineParser.parse(line);

                String code = normalize(val(cols, codeIdx));
                String name = normalize(val(cols, nameIdx));
                if (code.isBlank() || name.isBlank()) continue;

                Market market;
                try {
                    market = Market.from(val(cols, marketIdx));
                } catch (Exception e) {
                    // 시장값이 이상하면 skip
                    continue;
                }

                String industryName = normalize(val(cols, industryIdx));
                Sector sector = unknown;

                if (!industryName.isBlank()) {
                    // sector csv에 없어도 stock csv에서 보강 + 중복 제거
                    sector = sectorByName.computeIfAbsent(industryName,
                            k -> sectorRepository.save(Sector.of(k)));
                }

                Stock stock = stockRepository.findByCode(code).orElse(null);
                if (stock == null) {
                    stock = Stock.of(code, name, market, sector);
                    batch.add(stock);
                } else {
                    stock.update(name, market, sector);
                }

                if (batch.size() >= 500) {
                    stockRepository.saveAll(batch);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                stockRepository.saveAll(batch);
            }
        }
    }

    private static int indexOf(List<String> headers, String name) {
        for (int i = 0; i < headers.size(); i++) {
            if (name.equals(headers.get(i))) return i;
        }
        return -1;
    }

    private static String val(List<String> cols, int idx) {
        if (idx < 0 || idx >= cols.size()) return null;
        return cols.get(idx);
    }

    private static String normalize(String v) {
        if (v == null) return "";
        return v.trim().replaceAll("\\s+", " ");
    }

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.parseLong(s.replaceAll("[,_\\s]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s.replace("%", "").trim());
        } catch (Exception e) {
            return null;
        }
    }
}