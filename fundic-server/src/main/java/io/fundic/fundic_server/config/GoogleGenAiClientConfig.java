package io.fundic.fundic_server.config;


import com.google.adk.models.Gemini;
import com.google.genai.Client;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(GoogleGenAiProperties.class)
public class GoogleGenAiClientConfig {

    private final GoogleGenAiProperties props;

    @Bean
    public Client genaiClient() {
        Client.Builder b = Client.builder();

        if (props.getGenai().isUseVertexai()) {
            // Vertex AI backend (project/location 권장)
            b.vertexAI(true);

            if (props.getGenai().getProject() != null && !props.getGenai().getProject().isBlank()) {
                b.project(props.getGenai().getProject());
            }
            if (props.getGenai().getLocation() != null && !props.getGenai().getLocation().isBlank()) {
                b.location(props.getGenai().getLocation());
            }

            // Express mode(키 기반) 쓸 때만 apiKey도 함께 세팅 가능
            if (props.hasApiKey()) {
                b.apiKey(props.getApiKey());
            }
        } else {
            // Gemini Developer API(AI Studio) backend → apiKey 필수
            if (!props.hasApiKey()) {
                throw new IllegalStateException("google.api-key is empty. Set google.api-key or GOOGLE_API_KEY.");
            }
            b.vertexAI(false).apiKey(props.getApiKey());
        }

        return b.build(); // java-genai 공식 builder 패턴 :contentReference[oaicite:2]{index=2}
    }

    /**
     * gemini-3-flash-preview (Gemini 3 Flash Preview) — 속도/품질 균형 좋음
     * gemini-2.5-flash-lite (Gemini 2.5 Flash-Lite) — 대규모 사용을 위해 빌드된 가장 작고 비용 효율적인 모델
     * gemini-2.5-flash-preview-09-2025
     * Flash 모델을 기반으로 하는 최신 모델입니다. 2.5 Flash Preview는 대규모 처리, 짧은 지연 시간,
     * 사고력이 필요한 대량 작업, 에이전트 사용 사례에 가장 적합
     * */
    @Bean
    public Gemini geminiModel(Client client) {
        return new Gemini("gemini-3-flash-preview", client);
    }
}