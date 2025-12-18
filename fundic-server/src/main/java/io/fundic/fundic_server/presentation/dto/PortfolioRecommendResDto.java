package io.fundic.fundic_server.presentation.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class PortfolioRecommendResDto {
    private Map<String, Integer> sectorWeights; // sectorId -> %
    private Map<String, List<StockPick>> stockPicksBySector;
    private List<String> rebalancingRules; // 3개
    private String caution; // 1개
    private Map<String, Object> meta; // 선택

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
