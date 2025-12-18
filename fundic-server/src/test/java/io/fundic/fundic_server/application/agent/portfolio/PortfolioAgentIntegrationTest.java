package io.fundic.fundic_server.application.agent.portfolio;

import io.fundic.fundic_server.application.agent.stock.StockSelectionOutput;
import io.fundic.fundic_server.presentation.dto.UserProfileRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PortfolioAgent 통합 테스트
 */
@SpringBootTest
@ActiveProfiles("local")
class PortfolioAgentIntegrationTest {

    @Autowired
    private PortfolioAgent portfolioAgent;

    @Test
    void testPortfolioOptimization() {
        // Given
        UserProfileRequest userProfile = new UserProfileRequest();
        userProfile.setHorizon(UserProfileRequest.Horizon.M6P);
        userProfile.setRisk(UserProfileRequest.Risk.MEDIUM);
        userProfile.setGoal(UserProfileRequest.Goal.BALANCED);
        userProfile.setThemeKeywords(List.of("반도체", "AI"));

        // 섹터별 종목 선택 결과 (Mock)
        StockSelectionOutput leaderStocks = new StockSelectionOutput(List.of(
                new StockSelectionOutput.SelectedStock("005930", "삼성전자", 85,
                        "반도체 업황 회복 기대", "미중 무역 분쟁 리스크"),
                new StockSelectionOutput.SelectedStock("000660", "SK하이닉스", 80,
                        "메모리 반도체 수요 증가", "재고 조정 리스크")
        ));

        StockSelectionOutput supportStocks = new StockSelectionOutput(List.of(
                new StockSelectionOutput.SelectedStock("035420", "NAVER", 75,
                        "AI 서비스 확대", "경쟁 심화"),
                new StockSelectionOutput.SelectedStock("035720", "카카오", 70,
                        "플랫폼 안정성", "규제 리스크")
        ));

        StockSelectionOutput bufferStocks = new StockSelectionOutput(List.of(
                new StockSelectionOutput.SelectedStock("051910", "LG화학", 72,
                        "2차전지 성장", "원자재 가격 변동"),
                new StockSelectionOutput.SelectedStock("006400", "삼성SDI", 68,
                        "전기차 수요 증가", "중국 경쟁사 위협")
        ));

        PortfolioInput input = new PortfolioInput(
                new PortfolioInput.SectorInfo("1", "반도체", leaderStocks),
                new PortfolioInput.SectorInfo("2", "IT서비스", supportStocks),
                new PortfolioInput.SectorInfo("3", "화학", bufferStocks),
                userProfile
        );

        // When
        PortfolioOutput output = portfolioAgent.execute(input);

        // Then
        assertThat(output).isNotNull();

        // 섹터 비중 검증
        assertThat(output.sectorWeights()).isNotNull();
        double totalWeight = output.sectorWeights().leader() +
                output.sectorWeights().support() +
                output.sectorWeights().buffer();
        assertThat(totalWeight).isBetween(99.0, 101.0);  // 오차 허용

        // 종목 배분 검증
        assertThat(output.stockAllocations()).isNotEmpty();

        // 리밸런싱 룰 검증
        assertThat(output.rebalancingRules()).isNotEmpty();

        // 설명 검증
        assertThat(output.explanation()).isNotBlank();

        // 결과 출력
        System.out.println("\n=== Portfolio Optimization Result ===");
        System.out.printf("섹터 비중 - Leader: %.1f%%, Support: %.1f%%, Buffer: %.1f%%\n",
                output.sectorWeights().leader(),
                output.sectorWeights().support(),
                output.sectorWeights().buffer());

        System.out.println("\n종목 배분:");
        output.stockAllocations().forEach(alloc ->
                System.out.printf("  [%s] %s (%s): %.1f%%\n",
                        alloc.stockCode(), alloc.stockName(), alloc.sector(), alloc.weight())
        );

        System.out.println("\n리밸런싱 룰:");
        output.rebalancingRules().forEach(rule ->
                System.out.printf("  - %s\n", rule)
        );

        System.out.println("\n포트폴리오 설명:");
        System.out.println("  " + output.explanation());
    }
}
