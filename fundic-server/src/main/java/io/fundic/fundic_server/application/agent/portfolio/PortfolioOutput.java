package io.fundic.fundic_server.application.agent.portfolio;

import java.util.List;

/**
 * PortfolioAgent 출력 데이터
 */
public record PortfolioOutput(
        SectorWeights sectorWeights,
        List<StockAllocation> stockAllocations,
        List<String> rebalancingRules,
        String explanation
) {
    /**
     * 섹터별 비중
     */
    public record SectorWeights(
            double leader,
            double support,
            double buffer
    ) {
        public SectorWeights {
            // 검증: 비중 합계가 100%인지 확인 (오차 허용 ±1%)
            double total = leader + support + buffer;
            if (total < 99.0 || total > 101.0) {
                throw new IllegalArgumentException(
                        String.format("Sector weights must sum to 100%% (got %.2f%%)", total)
                );
            }
        }
    }

    /**
     * 개별 종목 배분 정보
     */
    public record StockAllocation(
            String stockCode,
            String stockName,
            String sector,  // "leader", "support", "buffer"
            double weight   // 포트폴리오 내 비중 (%)
    ) {}
}
