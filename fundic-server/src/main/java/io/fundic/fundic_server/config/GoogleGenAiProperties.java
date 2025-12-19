package io.fundic.fundic_server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "google")
public class GoogleGenAiProperties {

    private String apiKey;
    private Genai genai = new Genai();

    @Getter @Setter
    public static class Genai {
        private boolean useVertexai = false;
        private String project;
        private String location;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}