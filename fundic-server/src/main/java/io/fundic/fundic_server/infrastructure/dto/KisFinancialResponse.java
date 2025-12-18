package io.fundic.fundic_server.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * KIS API 재무제표 조회 응답
 */
public record KisFinancialResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg_cd") String msgCd,
        @JsonProperty("msg1") String msg1,
        @JsonProperty("output") Output output
) {
    public record Output(
            @JsonProperty("per") String per,                     // PER
            @JsonProperty("pbr") String pbr,                     // PBR
            @JsonProperty("roe") String roe,                     // ROE
            @JsonProperty("eps") String eps,                     // EPS
            @JsonProperty("bps") String bps,                     // BPS
            @JsonProperty("rsrv_rate") String rsrvRate,          // 유보율
            @JsonProperty("sale_otrt") String saleOtrt,          // 매출액영업이익률
            @JsonProperty("debt_rate") String debtRate           // 부채비율
    ) {}

    public boolean isSuccess() {
        return "0".equals(rtCd);
    }
}
