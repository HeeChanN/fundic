package io.fundic.fundic_server.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SectorScore {
    private String sectorId;
    private int momentumScore;   // 0~100
    private int riskScore;       // 0~100 (높을수록 안정)
    private int diversifyScore;  // 0~100
    private int eventScore;      // 0~100
    private int totalScore;      // 0~100
}
