package io.fundic.fundic_server.application;

import io.fundic.fundic_server.presentation.dto.UserProfileRequest;

public class WeightPolicy {

    public record Weights(double momentum, double risk, double diversify, double event) {}

    public static Weights from(UserProfileRequest.Risk risk, UserProfileRequest.Goal goal) {
        // MVP: goal이 THEME여도 risk 기반 가중치 유지(나중에 goal별로 세분화 가능)
        return switch (risk) {
            case HIGH -> new Weights(0.55, 0.15, 0.20, 0.10);
            case MEDIUM -> new Weights(0.40, 0.25, 0.25, 0.10);
            case LOW -> new Weights(0.25, 0.40, 0.25, 0.10);
        };
    }
}
