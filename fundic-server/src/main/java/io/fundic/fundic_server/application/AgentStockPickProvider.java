package io.fundic.fundic_server.application;

import io.fundic.fundic_server.application.agent.stock.StockSelectionAgent;
import io.fundic.fundic_server.application.agent.stock.StockSelectionInput;
import io.fundic.fundic_server.application.agent.stock.StockSelectionOutput;
import io.fundic.fundic_server.presentation.dto.PortfolioRecommendResDto;
import io.fundic.fundic_server.presentation.dto.UserProfileRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * StockSelectionAgent를 사용하는 종목 선택 Provider
 * AI 기반 종목 분석 및 추천
 */
@Slf4j
@Component
@Primary
@Profile("local")
@RequiredArgsConstructor
public class AgentStockPickProvider implements StockPickProvider {

    private final StockSelectionAgent stockSelectionAgent;
    private final SectorConstituentsProvider constituentsProvider;
    private final SectorNameProvider sectorNameProvider;

    @Override
    public List<PortfolioRecommendResDto.StockPick> pickTopStocks(String sectorId, UserProfileRequest profile) {
        log.info("Picking top stocks for sector {} using AI Agent", sectorId);

        try {
            // 섹터 구성 종목 조회
            List<String> stockCodes = constituentsProvider.getStockCodesBySector(sectorId);
            if (stockCodes.isEmpty()) {
                log.warn("No stocks found for sector {}", sectorId);
                return List.of();
            }

            // 섹터명 조회
            String sectorName = sectorNameProvider.findNameById(sectorId)
                    .orElse("섹터" + sectorId);

            // StockSelectionAgent 실행
            Long sectorIdLong = Long.parseLong(sectorId);
            StockSelectionInput input = new StockSelectionInput(
                    sectorIdLong,
                    sectorName,
                    stockCodes,
                    profile
            );

            StockSelectionOutput output = stockSelectionAgent.execute(input);

            // PortfolioRecommendResDto.StockPick으로 변환
            return output.selectedStocks().stream()
                    .map(stock -> PortfolioRecommendResDto.StockPick.builder()
                            .code(stock.stockCode())
                            .name(stock.stockName())
                            .score(stock.score())
                            .reasons(List.of(stock.reason()))
                            .build())
                    .toList();

        } catch (Exception e) {
            log.error("Failed to pick stocks using Agent for sector {}", sectorId, e);
            // Fallback: 빈 리스트 반환 (또는 기존 rule-based 로직 호출)
            return List.of();
        }
    }
}
