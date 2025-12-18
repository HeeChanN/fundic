package io.fundic.fundic_server.application;


import java.util.Map;

public interface StockSectorMapProvider {
    // stockCode -> sectorId
    Map<String, String> loadStockToSectorMap();
}
