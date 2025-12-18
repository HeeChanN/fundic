package io.fundic.fundic_server.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Google API 설정
 * application.yml의 google.api-key 값을 읽어 시스템 환경 변수로 설정
 */
@Slf4j
@Getter
@Configuration
public class GoogleApiConfig {

    @Value("${google.api-key}")
    private String apiKey;

    /**
     * Spring 초기화 시 Google API 키를 환경 변수로 설정
     * Google ADK는 GOOGLE_API_KEY 환경 변수를 자동으로 읽습니다
     */
    @PostConstruct
    public void init() {
        if (apiKey != null && !apiKey.isBlank()) {
            System.setProperty("GOOGLE_API_KEY", apiKey);
            log.info("Google API key configured from application.yml");
        } else {
            log.warn("Google API key is not configured. Please set 'google.api-key' in application.yml or GOOGLE_API_KEY environment variable");
        }
    }

    /**
     * API 키가 설정되었는지 확인
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
