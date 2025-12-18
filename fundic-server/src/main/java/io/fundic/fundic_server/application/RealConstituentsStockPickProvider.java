package io.fundic.fundic_server.application;

import io.fundic.fundic_server.presentation.dto.PortfolioRecommendResDto;
import io.fundic.fundic_server.presentation.dto.UserProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@Profile("local")
@RequiredArgsConstructor
public class RealConstituentsStockPickProvider implements StockPickProvider {

    private final SectorConstituentsProvider constituentsProvider;
    private final StockScoreProvider scoreProvider;
    private final StockNameProvider stockNameProvider;

    @Override
    public List<PortfolioRecommendResDto.StockPick> pickTopStocks(String sectorId, UserProfileRequest profile) {
        List<String> stockCodes = constituentsProvider.getStockCodesBySector(sectorId);
        if (stockCodes.isEmpty()) return List.of();

        Map<String, Integer> scores = scoreProvider.scoreStocks(stockCodes);

        return stockCodes.stream()
                .filter(scores::containsKey)
                .sorted(Comparator.comparingInt((String c) -> scores.get(c)).reversed())
                .limit(4)
                .map(code -> PortfolioRecommendResDto.StockPick.builder()
                        .code(code)
                        .name(stockNameProvider.findNameByCode(code).orElse(code))
                        .score(scores.get(code))
                        .reasons(List.of("섹터 구성 종목 기반 선별", "점수 상위"))
                        .build())
                .toList();
    }
}