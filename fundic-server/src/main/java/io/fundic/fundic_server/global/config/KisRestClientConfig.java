package io.fundic.fundic_server.global.config;

import io.fundic.fundic_server.infrastructure.KisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class KisRestClientConfig {

    @Bean
    public RestClient kisMockAuthRestClient(KisProperties props) {
        return RestClient.builder()
                .baseUrl(props.mockBaseUrl())
                .build();
    }
}
