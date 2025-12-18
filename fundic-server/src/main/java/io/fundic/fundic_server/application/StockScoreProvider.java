package io.fundic.fundic_server.application;

import java.util.Map;

public interface StockScoreProvider {
    // stockCode -> score(0~100)
    Map<String, Integer> scoreStocks(Iterable<String> stockCodes);
}
