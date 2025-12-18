package io.fundic.fundic_server.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Map;

@Getter
@Builder
public class SectorSnapshot {
    private String sectorId;
    private String sectorName;  // 섹터명 (단순화를 위해 추가)
    private LocalDate asOf;

    // Momentum
    private double ret1m;      // 0.04 = +4%
    private double ret3m;
    private double trend20d;   // -1~+1 (기울기/정규화)

    // Risk
    private double vol20d;     // 0.22
    private double mdd60d;     // -0.11

    // Event (아주 단순)
    private int posNews;
    private int negNews;

    // Corr to other sectors (same universe)
    private Map<String, Double> correlation;
}
