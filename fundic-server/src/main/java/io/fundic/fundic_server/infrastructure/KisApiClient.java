package io.fundic.fundic_server.infrastructure;

import io.fundic.fundic_server.infrastructure.dto.KisTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

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
}
