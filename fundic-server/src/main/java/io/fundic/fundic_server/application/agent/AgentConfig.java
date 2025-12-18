package io.fundic.fundic_server.application.agent;

import lombok.Builder;

@Builder
public record AgentConfig(
        String model,
        Double temperature,
        Integer maxTokens,
        String systemPrompt
) {
    public static AgentConfig withDefaults(String systemPrompt) {
        return AgentConfig.builder()
                .model("gemini-2.0-flash-001")
                .temperature(0.3)
                .maxTokens(8192)
                .systemPrompt(systemPrompt)
                .build();
    }
}
