package io.fundic.fundic_server.ai;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * AI 종목 분석 결과
 */
@Getter
@Builder
public class StockAnalysisResult {
    private String stockCode;
    private String stockName;
    private String analysisText;
    private int dataPoints;
    private int latestPrice;
    private LocalDate analyzedAt;
}
