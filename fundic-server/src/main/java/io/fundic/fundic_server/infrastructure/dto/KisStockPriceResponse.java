package io.fundic.fundic_server.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * KIS API 현재가 조회 응답
 */
public record KisStockPriceResponse(
        @JsonProperty("rt_cd") String rtCd,         // 응답코드
        @JsonProperty("msg_cd") String msgCd,        // 메시지코드
        @JsonProperty("msg1") String msg1,           // 메시지
        @JsonProperty("output") Output output        // 응답 데이터
) {
    public record Output(
            @JsonProperty("stck_prpr") String stckPrpr,          // 현재가
            @JsonProperty("prdy_vrss") String prdyVrss,          // 전일대비
            @JsonProperty("prdy_ctrt") String prdyCtrt,          // 전일대비율
            @JsonProperty("acml_vol") String acmlVol,            // 누적 거래량
            @JsonProperty("stck_hgpr") String stckHgpr,          // 최고가
            @JsonProperty("stck_lwpr") String stckLwpr,          // 최저가
            @JsonProperty("stck_oprc") String stckOprc,          // 시가
            @JsonProperty("hts_avls") String htsAvls             // 시가총액 (백만)
    ) {}

    public boolean isSuccess() {
        return "0".equals(rtCd);
    }
}
