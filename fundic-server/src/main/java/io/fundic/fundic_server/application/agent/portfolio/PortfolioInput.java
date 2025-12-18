package io.fundic.fundic_server.application.agent.portfolio;

import io.fundic.fundic_server.application.agent.stock.StockSelectionOutput;
import io.fundic.fundic_server.presentation.dto.UserProfileRequest;

/**
 * PortfolioAgent 입력 데이터
 */
public record PortfolioInput(
        SectorInfo leaderSector,
        SectorInfo supportSector,
        SectorInfo bufferSector,
        UserProfileRequest userProfile
) {
    /**
     * 섹터 정보 (ID, 이름, 추천 종목 목록)
     */
    public record SectorInfo(
            String sectorId,
            String sectorName,
            StockSelectionOutput stockSelection
    ) {}
}
