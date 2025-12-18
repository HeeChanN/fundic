package io.fundic.fundic_server.application;

import io.fundic.fundic_server.CsvStockNameMapLoader;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CachedStockNameProvider implements StockNameProvider {

    private final CsvStockNameMapLoader loader;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public CachedStockNameProvider(CsvStockNameMapLoader loader) {
        this.loader = loader;
    }

    @PostConstruct
    public void init() {
        cache.putAll(loader.loadCodeToName());
    }

    @Override
    public Optional<String> findNameByCode(String code) {
        return Optional.ofNullable(cache.get(code));
    }
}