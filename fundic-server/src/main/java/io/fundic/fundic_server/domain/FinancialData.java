package io.fundic.fundic_server.domain;

/**
 * 재무제표 데이터
 */
public record FinancialData(
        String stockCode,
        Double per,             // 주가수익비율 (Price Earnings Ratio)
        Double pbr,             // 주가순자산비율 (Price Book-value Ratio)
        Double roe,             // 자기자본이익률 (Return On Equity, %)
        Double operatingMargin, // 영업이익률 (%)
        Double debtRatio,       // 부채비율 (%)
        Long marketCap,         // 시가총액 (백만원)
        Double eps              // 주당순이익 (Earnings Per Share)
) {}
