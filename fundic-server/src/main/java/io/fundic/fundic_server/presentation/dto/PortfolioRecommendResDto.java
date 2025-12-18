package io.fundic.fundic_server.presentation.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class PortfolioRecommendResDto {
    private Map<String, Integer> sectorWeights; // sectorId -> % (deprecated, 호환성 유지)
    private Map<String, List<StockPick>> stockPicksBySector; // deprecated, 호환성 유지
    private List<StockAllocation> stocks; // 전체 포트폴리오 종목 (10개)
    private List<String> rebalancingRules; // 3개
    private String caution; // 1개
    private Map<String, Object> meta; // 선택

    /**
     * 개별 종목 정보
     */
    @Getter
    @Builder
    public static class StockAllocation {
        private String code;        // 종목 코드
        private String name;        // 종목명
        private String sectorName;  // 섹터명 (분야)
        private String market;      // 시장 (KOSPI/KOSDAQ)
        private double weight;      // 포트폴리오 비중 (%)
    }

    /**
     * @deprecated 호환성을 위해 유지, stocks 필드 사용 권장
     */
    @Deprecated
    @Getter
    @Builder
    public static class StockPick {
        private String code;
        private String name;
        private int score; // 0~100 (초기 stub 가능)
        private List<String> reasons;
        private Integer weight; // 종목 비중(선택)
    }
}
