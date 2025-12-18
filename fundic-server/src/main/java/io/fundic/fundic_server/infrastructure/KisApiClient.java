package io.fundic.fundic_server.infrastructure;

import io.fundic.fundic_server.infrastructure.dto.KisFinancialResponse;
import io.fundic.fundic_server.infrastructure.dto.KisStockPriceResponse;
import io.fundic.fundic_server.infrastructure.dto.KisTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisApiClient {
    private final RestClient restClient;
    private final KisProperties properties;

    public KisTokenResponse issueAccessToken() {
        Map<String, String> body = Map.of(
                "grant_type", "client_credentials",
                "appkey", properties.mockAppKey(),
                "appsecret", properties.mockAppSecret()
        );

        return restClient.post()
                .uri("/oauth2/tokenP")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(KisTokenResponse.class);
    }

    /**
     * 국내주식 현재가 시세 조회
     *
     * @param accessToken 액세스 토큰
     * @param stockCode 종목코드 (6자리)
     * @param marketCode 시장구분코드 (J: KOSPI, Q: KOSDAQ)
     * @return 주식 현재가 정보
     */
    public KisStockPriceResponse fetchStockPrice(String accessToken, String stockCode, String marketCode) {
        log.debug("Fetching stock price for: {}, market: {}", stockCode, marketCode);

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", marketCode)
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", properties.mockAppKey())
                .header("appsecret", properties.mockAppSecret())
                .header("tr_id", "VHKST01010100")  // 모의투자용 tr_id
                .retrieve()
                .body(KisStockPriceResponse.class);
    }

    /**
     * 국내주식 기본정보 조회 (재무제표 포함)
     *
     * @param accessToken 액세스 토큰
     * @param stockCode 종목코드 (6자리)
     * @return 주식 재무정보
     */
    public KisFinancialResponse fetchFinancialInfo(String accessToken, String stockCode) {
        log.debug("Fetching financial info for: {}", stockCode);

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/search-stock-info")
                        .queryParam("PDNO", stockCode)
                        .queryParam("PRDT_TYPE_CD", "300")  // 주식
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", properties.mockAppKey())
                .header("appsecret", properties.mockAppSecret())
                .header("tr_id", "VTPF1002R")  // 모의투자용 tr_id
                .retrieve()
                .body(KisFinancialResponse.class);
    }
}
