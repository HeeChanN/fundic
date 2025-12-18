package io.fundic.fundic_server.application;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CachedSectorConstituentsProvider implements SectorConstituentsProvider {

    private final StockSectorMapProvider mapProvider;

    // sectorId -> stockCodes
    private final Map<String, List<String>> cache = new ConcurrentHashMap<>();

    public CachedSectorConstituentsProvider(StockSectorMapProvider mapProvider) {
        this.mapProvider = mapProvider;
    }

    @PostConstruct
    public void init() {
        Map<String, String> stockToSector = mapProvider.loadStockToSectorMap();
        Map<String, List<String>> tmp = new HashMap<>();

        for (Map.Entry<String, String> e : stockToSector.entrySet()) {
            tmp.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        }

        // 정렬(가독성/재현성)
        for (Map.Entry<String, List<String>> e : tmp.entrySet()) {
            e.getValue().sort(Comparator.naturalOrder());
            cache.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
    }

    @Override
    public List<String> getStockCodesBySector(String sectorId) {
        return cache.getOrDefault(sectorId, List.of());
    }
}
