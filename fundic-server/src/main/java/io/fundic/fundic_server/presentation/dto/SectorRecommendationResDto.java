package io.fundic.fundic_server.presentation.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class SectorRecommendationResDto {
    private SelectedSectors selected;
    private SwapOptions swapOptions;
    private List<SectorCard> cards;
    private Map<String, Object> meta; // 디버그/추적용(선택)

    @Getter @Builder
    public static class SelectedSectors {
        private String leader;
        private String support;
        private String buffer;
    }

    @Getter @Builder
    public static class SwapOptions {
        private List<String> leader;
        private List<String> support;
        private List<String> buffer;
    }

    @Getter @Builder
    public static class SectorCard {
        private String role;           // LEADER / SUPPORT / BUFFER
        private String sectorId;       // e.g. SEMICONDUCTOR
        private String title;          // UI title
        private String oneLineReason;  // 1줄
        private List<Kpi> kpis;        // 숫자 2개
        private String caution;        // 주의 1개
    }

    @Getter
    @Builder
    public static class Kpi {
        private String label;
        private String value;
    }
}
