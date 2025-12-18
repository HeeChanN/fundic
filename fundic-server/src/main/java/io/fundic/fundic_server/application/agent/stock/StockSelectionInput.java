package io.fundic.fundic_server.application.agent.stock;

import io.fundic.fundic_server.presentation.dto.UserProfileRequest;

import java.util.List;

/**
 * 종목 선택 Agent 입력 데이터
 */
public record StockSelectionInput(
        Long sectorId,
        String sectorName,
        List<String> stockCodes,
        UserProfileRequest userProfile
) {}
