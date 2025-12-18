package io.fundic.fundic_server.application;

import io.fundic.fundic_server.domain.StockData;

import java.util.List;
import java.util.Map;

/**
 * 종목 데이터 제공자
 */
public interface StockDataProvider {
    /**
     * 여러 종목의 데이터를 한번에 조회
     * @param stockCodes 종목 코드 리스트
     * @return 종목 코드 -> 종목 데이터 맵
     */
    Map<String, StockData> getStockDataBatch(List<String> stockCodes);

    /**
     * 단일 종목 데이터 조회
     */
    StockData getStockData(String stockCode);
}
