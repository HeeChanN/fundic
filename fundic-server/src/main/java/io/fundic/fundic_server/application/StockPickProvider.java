package io.fundic.fundic_server.application;

import io.fundic.fundic_server.presentation.dto.PortfolioRecommendResDto;
import io.fundic.fundic_server.presentation.dto.UserProfileRequest;

import java.util.List;

public interface StockPickProvider {
    List<PortfolioRecommendResDto.StockPick> pickTopStocks(String sectorId, UserProfileRequest profile);
}
