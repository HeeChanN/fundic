package io.fundic.fundic_server.ai;

import io.fundic.fundic_server.domain.Stock;
import io.fundic.fundic_server.domain.StockPriceHistory;
import io.fundic.fundic_server.domain.FinancialDataHistory;
import io.fundic.fundic_server.infrastructure.sector.StockRepository;
import io.fundic.fundic_server.infrastructure.sector.StockPriceHistoryRepository;
import io.fundic.fundic_server.infrastructure.sector.FinancialDataHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 기반 종목 분석 서비스
 * Google ADK를 사용하여 종목 데이터를 분석하고 투자 의견 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockAnalysisService {

    private final StockRepository stockRepository;
    private final StockPriceHistoryRepository priceHistoryRepository;
    private final FinancialDataHistoryRepository financialDataHistoryRepository;

    /**
     * 종목 분석 수행
     *
     * @param stockCode 종목 코드
     * @return 분석 결과
     */
    public StockAnalysisResult analyzeStock(String stockCode) {
        log.info("AI 종목 분석 시작: {}", stockCode);

        // 1. 종목 조회
        Stock stock = stockRepository.findByCode(stockCode)
                .orElseThrow(() -> new RuntimeException("종목을 찾을 수 없습니다: " + stockCode));

        // 2. 주가 데이터 조회 (최근 60일)
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(60);
        List<StockPriceHistory> priceHistory = priceHistoryRepository
                .findByStockAndTradeDateBetweenOrderByTradeDateAsc(stock, startDate, endDate);

        if (priceHistory.isEmpty()) {
            throw new RuntimeException("주가 데이터가 없습니다: " + stockCode);
        }

        // 3. 재무 데이터 조회 (최신 4분기)
        List<FinancialDataHistory> financialHistory = financialDataHistoryRepository
                .findTop4ByStockOrderByFiscalYearDescFiscalQuarterDesc(stock.getId());

        // 4. 데이터를 텍스트로 변환
        String stockDataText = buildStockDataText(stock, priceHistory, financialHistory);

        // 5. Google ADK Agent 실행
        String analysisResult = runAIAnalysis(stock, stockDataText);

        // 6. 결과 반환
        return StockAnalysisResult.builder()
                .stockCode(stockCode)
                .stockName(stock.getName())
                .analysisText(analysisResult)
                .dataPoints(priceHistory.size())
                .latestPrice(priceHistory.get(priceHistory.size() - 1).getClosePrice())
                .analyzedAt(LocalDate.now())
                .build();
    }

    /**
     * 주가 및 재무 데이터를 텍스트로 변환
     */
    private String buildStockDataText(Stock stock,
                                      List<StockPriceHistory> priceHistory,
                                      List<FinancialDataHistory> financialHistory) {
        StringBuilder sb = new StringBuilder();

        // 종목 기본 정보
        sb.append("# 종목 정보\n");
        sb.append(String.format("- 종목명: %s\n", stock.getName()));
        sb.append(String.format("- 종목코드: %s\n", stock.getCode()));
        sb.append(String.format("- 시장: %s\n", stock.getMarket()));
        sb.append(String.format("- 섹터: %s\n\n", stock.getSector() != null ? stock.getSector().getName() : "N/A"));

        // 최근 60일 주가 데이터
        sb.append("# 최근 60일 주가 데이터\n");
        sb.append("날짜 | 종가 | RSI | MACD | MA20 | MA60\n");
        sb.append("---|---|---|---|---|---\n");

        // 최근 10일만 표시 (너무 길면 토큰 소모)
        List<StockPriceHistory> recentPrices = priceHistory.subList(
                Math.max(0, priceHistory.size() - 10),
                priceHistory.size()
        );

        for (StockPriceHistory price : recentPrices) {
            sb.append(String.format("%s | %,d원 | %.1f | %.2f | %.0f | %.0f\n",
                    price.getTradeDate(),
                    price.getClosePrice(),
                    price.getRsi() != null ? price.getRsi() : 0.0,
                    price.getMacd() != null ? price.getMacd() : 0.0,
                    price.getMa20() != null ? price.getMa20() : 0.0,
                    price.getMa60() != null ? price.getMa60() : 0.0
            ));
        }

        // 주가 통계
        StockPriceHistory latest = priceHistory.get(priceHistory.size() - 1);
        StockPriceHistory oldest = priceHistory.get(0);
        double priceChange = ((double) (latest.getClosePrice() - oldest.getClosePrice()) / oldest.getClosePrice()) * 100;

        sb.append("\n# 60일 주가 통계\n");
        sb.append(String.format("- 현재가: %,d원\n", latest.getClosePrice()));
        sb.append(String.format("- 60일 전: %,d원\n", oldest.getClosePrice()));
        sb.append(String.format("- 변동률: %.2f%%\n", priceChange));
        sb.append(String.format("- 현재 RSI: %.1f\n", latest.getRsi() != null ? latest.getRsi() : 0.0));
        sb.append(String.format("- 현재 MACD: %.2f\n", latest.getMacd() != null ? latest.getMacd() : 0.0));
        sb.append(String.format("- MA20: %.0f원\n", latest.getMa20() != null ? latest.getMa20() : 0.0));
        sb.append(String.format("- MA60: %.0f원\n\n", latest.getMa60() != null ? latest.getMa60() : 0.0));

        // 재무 데이터
        if (!financialHistory.isEmpty()) {
            sb.append("# 재무 데이터 (최근 4분기)\n");
            sb.append("기간 | PER | PBR | ROE | 부채비율\n");
            sb.append("---|---|---|---|---\n");

            for (FinancialDataHistory financial : financialHistory) {
                sb.append(String.format("%d년 %dQ | %.1f | %.2f | %.2f%% | %.1f%%\n",
                        financial.getFiscalYear(),
                        financial.getFiscalQuarter(),
                        financial.getPer() != null ? financial.getPer() : 0.0,
                        financial.getPbr() != null ? financial.getPbr() : 0.0,
                        financial.getRoe() != null ? financial.getRoe() : 0.0,
                        financial.getDebtRatio() != null ? financial.getDebtRatio() : 0.0
                ));
            }
        }

        return sb.toString();
    }

    /**
     * AI 종목 분석 (Rule-based)
     * TODO: Google ADK 또는 LLM API 연동 예정
     */
    private String runAIAnalysis(Stock stock, String stockDataText) {
        try {
            // 현재는 Rule-based 분석 사용
            // 향후 Google ADK LoopAgent 또는 Gemini API 연동 예정
            return generateRuleBasedAnalysis(stock, stockDataText);

        } catch (Exception e) {
            log.error("AI 분석 중 오류 발생", e);
            return generateFallbackAnalysis(stock, stockDataText);
        }
    }

    /**
     * Rule-based 종목 분석 생성
     */
    private String generateRuleBasedAnalysis(Stock stock, String stockDataText) {
        // stockDataText에서 데이터 파싱
        List<StockPriceHistory> priceHistory = priceHistoryRepository
                .findByStockAndTradeDateBetweenOrderByTradeDateAsc(
                        stock,
                        LocalDate.now().minusDays(60),
                        LocalDate.now()
                );

        if (priceHistory.isEmpty()) {
            return generateFallbackAnalysis(stock, stockDataText);
        }

        StockPriceHistory latest = priceHistory.get(priceHistory.size() - 1);
        StockPriceHistory oldest = priceHistory.get(0);

        // 변동률 계산
        double priceChangePercent = ((double) (latest.getClosePrice() - oldest.getClosePrice()) / oldest.getClosePrice()) * 100;

        // RSI 분석
        double rsi = latest.getRsi() != null ? latest.getRsi() : 50.0;
        String rsiAnalysis;
        if (rsi > 70) {
            rsiAnalysis = "**과매수 구간** (RSI: " + String.format("%.1f", rsi) + ") - 단기 조정 가능성이 있습니다.";
        } else if (rsi < 30) {
            rsiAnalysis = "**과매도 구간** (RSI: " + String.format("%.1f", rsi) + ") - 반등 가능성을 주목할 시점입니다.";
        } else {
            rsiAnalysis = "**적정 구간** (RSI: " + String.format("%.1f", rsi) + ") - 안정적인 흐름을 보이고 있습니다.";
        }

        // MACD 분석
        double macd = latest.getMacd() != null ? latest.getMacd() : 0.0;
        String macdAnalysis;
        if (macd > 0) {
            macdAnalysis = "**상승 추세** (MACD: " + String.format("%.2f", macd) + ") - 매수 모멘텀이 있습니다.";
        } else {
            macdAnalysis = "**하락 추세** (MACD: " + String.format("%.2f", macd) + ") - 매도 압력이 있습니다.";
        }

        // 이동평균선 분석
        double ma20 = latest.getMa20() != null ? latest.getMa20() : latest.getClosePrice();
        double ma60 = latest.getMa60() != null ? latest.getMa60() : latest.getClosePrice();
        String maAnalysis;
        if (latest.getClosePrice() > ma20 && latest.getClosePrice() > ma60) {
            maAnalysis = "**강세 구간** - 현재가가 MA20, MA60 모두 상회하여 상승 추세입니다.";
        } else if (latest.getClosePrice() < ma20 && latest.getClosePrice() < ma60) {
            maAnalysis = "**약세 구간** - 현재가가 MA20, MA60 모두 하회하여 하락 추세입니다.";
        } else {
            maAnalysis = "**혼조 구간** - 단기/중기 추세가 엇갈리고 있습니다.";
        }

        // 투자 의견 결정
        String opinion;
        int score = 0;
        if (rsi > 50) score++;
        if (macd > 0) score++;
        if (latest.getClosePrice() > ma20) score++;

        if (score >= 2) {
            opinion = "**매수** 📈";
        } else if (score == 1) {
            opinion = "**중립** ⚖️";
        } else {
            opinion = "**관망** 👀";
        }

        // 목표가 계산 (현재가 기준 ±5-10%)
        int targetPrice = (int) (latest.getClosePrice() * (1 + (score >= 2 ? 0.08 : (score == 1 ? 0.03 : -0.03))));
        int stopLoss = (int) (latest.getClosePrice() * 0.95);

        // 리포트 생성
        StringBuilder report = new StringBuilder();

        report.append("## 📊 기술적 분석\n\n");
        report.append("### RSI 지표\n");
        report.append(rsiAnalysis).append("\n\n");

        report.append("### MACD\n");
        report.append(macdAnalysis).append("\n\n");

        report.append("### 이동평균선\n");
        report.append(maAnalysis).append("\n");
        report.append("- **MA20**: ").append(String.format("%,d원", (int) ma20)).append("\n");
        report.append("- **MA60**: ").append(String.format("%,d원", (int) ma60)).append("\n");
        report.append("- **현재가**: ").append(String.format("%,d원", latest.getClosePrice())).append("\n\n");

        report.append("### 60일 수익률\n");
        report.append(String.format("- **%+.2f%%** (%,d원 → %,d원)\n\n",
                priceChangePercent,
                oldest.getClosePrice(),
                latest.getClosePrice()));

        // 재무 분석 (데이터 있으면)
        List<FinancialDataHistory> financials = financialDataHistoryRepository
                .findTop4ByStockOrderByFiscalYearDescFiscalQuarterDesc(stock.getId());

        if (!financials.isEmpty()) {
            FinancialDataHistory latestFinancial = financials.get(0);
            report.append("## 💰 재무 분석\n\n");
            report.append("### 최근 분기 (").append(latestFinancial.getFiscalYear())
                    .append("년 ").append(latestFinancial.getFiscalQuarter()).append("Q)\n");

            if (latestFinancial.getPer() != null) {
                report.append("- **PER**: ").append(String.format("%.1f", latestFinancial.getPer())).append("\n");
            }
            if (latestFinancial.getPbr() != null) {
                report.append("- **PBR**: ").append(String.format("%.2f", latestFinancial.getPbr())).append("\n");
            }
            if (latestFinancial.getRoe() != null) {
                report.append("- **ROE**: ").append(String.format("%.2f%%", latestFinancial.getRoe())).append("\n");
            }
            if (latestFinancial.getDebtRatio() != null) {
                report.append("- **부채비율**: ").append(String.format("%.1f%%", latestFinancial.getDebtRatio())).append("\n");
            }
            report.append("\n");
        }

        report.append("## 🎯 투자 의견\n\n");
        report.append("### 종합 의견\n");
        report.append(opinion).append("\n\n");

        report.append("### 가격 전망\n");
        report.append("- **현재가**: ").append(String.format("%,d원", latest.getClosePrice())).append("\n");
        report.append("- **목표가**: ").append(String.format("%,d원", targetPrice)).append("\n");
        report.append("- **손절가**: ").append(String.format("%,d원", stopLoss)).append("\n\n");

        report.append("### 투자 포인트\n");
        report.append("- 60일 수익률: ").append(String.format("%+.2f%%", priceChangePercent)).append("\n");
        report.append("- 기술적 지표 종합: ").append(score).append("/3 긍정\n");
        report.append("- 섹터: ").append(stock.getSector() != null ? stock.getSector().getName() : "N/A").append("\n\n");

        report.append("### 리스크 요인\n");
        if (rsi > 70) {
            report.append("- ⚠️ RSI 과매수 구간 - 단기 조정 가능성\n");
        }
        if (latest.getClosePrice() < ma60) {
            report.append("- ⚠️ 중기 이동평균선 하회 - 추세 약세\n");
        }
        report.append("- ⚠️ 이 분석은 자동화된 분석이며, 투자 결정은 신중히 하시기 바랍니다.\n\n");

        report.append("## 📈 추천 전략\n\n");
        report.append("### 투자 시나리오\n");
        if (score >= 2) {
            report.append("- **진입**: ").append(String.format("%,d원", (int)(latest.getClosePrice() * 0.98))).append(" 이하 분할 매수\n");
            report.append("- **목표 수익률**: +8~10%\n");
            report.append("- **투자 기간**: 중기 (1~3개월)\n");
        } else {
            report.append("- **진입**: 추가 하락 시 지지선 확인 후\n");
            report.append("- **목표 수익률**: +3~5%\n");
            report.append("- **투자 기간**: 단기 (1개월 이내)\n");
        }

        report.append("\n---\n");
        report.append("*이 분석은 AI 알고리즘 기반의 자동 분석입니다. 투자에 대한 최종 결정은 투자자 본인의 판단에 따라야 합니다.*");

        return report.toString();
    }

    /**
     * AI 분석 실패 시 기본 분석 제공
     */
    private String generateFallbackAnalysis(Stock stock, String stockDataText) {
        return String.format("""
                ## ⚠️ AI 분석 서비스 일시 중단

                현재 AI 분석 서비스에 일시적인 문제가 발생했습니다.
                아래는 수집된 데이터입니다:

                %s

                ---

                **참고사항**:
                - RSI 70 이상: 과매수 구간 (조정 가능성)
                - RSI 30 이하: 과매도 구간 (반등 가능성)
                - MACD > 0: 상승 추세
                - MACD < 0: 하락 추세
                - 현재가 > MA20: 단기 상승
                - 현재가 > MA60: 중기 상승

                전문적인 투자 결정은 반드시 추가 리서치를 통해 이루어져야 합니다.
                """, stockDataText);
    }
}
