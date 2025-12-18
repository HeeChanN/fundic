package io.fundic.fundic_server.application.agent.sector;

import io.fundic.fundic_server.domain.SectorSnapshot;
import io.fundic.fundic_server.presentation.dto.UserProfileRequest;

import java.util.List;

/**
 * 섹터 선택 Agent 입력
 */
public record SectorSelectionInput(
        List<SectorSnapshot> candidateSectors,
        UserProfileRequest userProfile
) {
}
