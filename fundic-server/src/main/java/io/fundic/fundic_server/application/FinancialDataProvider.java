package io.fundic.fundic_server.application;

import io.fundic.fundic_server.domain.FinancialData;

import java.util.List;
import java.util.Map;

/**
 * 재무제표 데이터 제공자
 */
public interface FinancialDataProvider {
    /**
     * 여러 종목의 재무 데이터를 한번에 조회
     * @param stockCodes 종목 코드 리스트
     * @return 종목 코드 -> 재무 데이터 맵
     */
    Map<String, FinancialData> getFinancialDataBatch(List<String> stockCodes);

    /**
     * 단일 종목 재무 데이터 조회
     */
    FinancialData getFinancialData(String stockCode);
}
