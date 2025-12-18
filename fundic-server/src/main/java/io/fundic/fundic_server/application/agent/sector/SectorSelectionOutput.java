package io.fundic.fundic_server.application.agent.sector;

/**
 * 섹터 선택 Agent 출력
 */
public record SectorSelectionOutput(
        SelectedSector leader,
        SelectedSector support,
        SelectedSector buffer,
        String explanation
) {
    public record SelectedSector(
            String sectorId,
            String sectorName,
            int score,
            String reason
    ) {
    }
}
