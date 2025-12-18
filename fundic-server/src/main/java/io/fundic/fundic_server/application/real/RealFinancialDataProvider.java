package io.fundic.fundic_server.application.real;

import io.fundic.fundic_server.application.FinancialDataProvider;
import io.fundic.fundic_server.domain.FinancialData;
import io.fundic.fundic_server.infrastructure.KisApiClient;
import io.fundic.fundic_server.infrastructure.dto.KisFinancialResponse;
import io.fundic.fundic_server.infrastructure.dto.KisTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 실제 KIS API를 사용하는 재무 데이터 제공자
 * production 환경에서 사용
 */
@Slf4j
@Component
@Profile("!local & !dev")
public class RealFinancialDataProvider implements FinancialDataProvider {

    private final KisApiClient kisApiClient;
    private String cachedAccessToken;

    public RealFinancialDataProvider(KisApiClient kisApiClient) {
        this.kisApiClient = kisApiClient;
    }

    @Override
    public Map<String, FinancialData> getFinancialDataBatch(List<String> stockCodes) {
        Map<String, FinancialData> result = new HashMap<>();

        // 토큰 발급 (배치 조회 시작 시 1회만)
        ensureAccessToken();

        for (String stockCode : stockCodes) {
            try {
                FinancialData financialData = getFinancialData(stockCode);
                if (financialData != null) {
                    result.put(stockCode, financialData);
                }
            } catch (Exception e) {
                log.error("Failed to fetch financial data for {}", stockCode, e);
            }
        }

        return result;
    }

    @Override
    public FinancialData getFinancialData(String stockCode) {
        try {
            ensureAccessToken();

            KisFinancialResponse response = kisApiClient.fetchFinancialInfo(
                    cachedAccessToken,
                    stockCode
            );

            if (response == null || !response.isSuccess()) {
                log.warn("Failed to fetch financial info for {}: {}", stockCode,
                        response != null ? response.msg1() : "null response");
                return null;
            }

            return convertToFinancialData(stockCode, response);

        } catch (Exception e) {
            log.error("Error fetching financial data for {}", stockCode, e);
            return null;
        }
    }

    /**
     * 액세스 토큰 확보 (캐시된 토큰이 없으면 새로 발급)
     */
    private void ensureAccessToken() {
        if (cachedAccessToken == null) {
            KisTokenResponse tokenResponse = kisApiClient.issueAccessToken();
            if (tokenResponse != null) {
                cachedAccessToken = tokenResponse.accessToken();
                log.info("Access token issued successfully");
            } else {
                throw new RuntimeException("Failed to issue access token");
            }
        }
    }

    /**
     * KIS API 응답을 FinancialData 도메인 객체로 변환
     */
    private FinancialData convertToFinancialData(String stockCode, KisFinancialResponse response) {
        KisFinancialResponse.Output output = response.output();

        double per = parseDoubleSafely(output.per());
        double pbr = parseDoubleSafely(output.pbr());
        double roe = parseDoubleSafely(output.roe());
        double operatingMargin = parseDoubleSafely(output.saleOtrt());
        double debtRatio = parseDoubleSafely(output.debtRate());
        double eps = parseDoubleSafely(output.eps());

        // 시가총액은 주가 API에서 가져오므로 여기서는 0으로 설정
        // TODO: 주가 API와 통합하여 시가총액 계산
        long marketCap = 0L;

        return new FinancialData(
                stockCode,
                per,
                pbr,
                roe,
                operatingMargin,
                debtRatio,
                marketCap,
                eps
        );
    }

    private double parseDoubleSafely(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse double: {}", value);
            return 0.0;
        }
    }
}
