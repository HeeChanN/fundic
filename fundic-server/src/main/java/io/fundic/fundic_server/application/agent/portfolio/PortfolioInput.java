package io.fundic.fundic_server.application.agent.portfolio;

import io.fundic.fundic_server.presentation.dto.UserProfileRequest;

/**
 * PortfolioAgent 입력 데이터
 * 섹터 ID만 받아서 agent가 종목 선택 + 포트폴리오 최적화를 모두 수행
 */
public record PortfolioInput(
        String leaderSectorId,
        String supportSectorId,
        String bufferSectorId,
        UserProfileRequest userProfile
) {}
