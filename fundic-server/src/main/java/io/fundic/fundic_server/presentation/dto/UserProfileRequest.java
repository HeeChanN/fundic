package io.fundic.fundic_server.presentation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class UserProfileRequest {

    private Horizon horizon;
    private Risk risk;
    private Goal goal;
    private List<String> themeKeywords;

    public enum Horizon { W1_4, M1_6, M6P }
    public enum Risk { LOW, MEDIUM, HIGH }
    public enum Goal { GROWTH, STABILITY, BALANCED, THEME }
}