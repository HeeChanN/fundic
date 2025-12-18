package io.fundic.fundic_server.domain;

/**
 * 종목 데이터 (가격, 기술적 지표)
 */
public record StockData(
        String stockCode,
        String stockName,
        Integer currentPrice,
        Long volume,
        Double changeRate,  // 전일 대비 변동률 (%)
        TechnicalIndicators technical
) {
    public record TechnicalIndicators(
            Double rsi,         // RSI (0-100)
            Double macd,        // MACD
            Double ma20,        // 20일 이동평균
            Double ma60         // 60일 이동평균
    ) {}
}
