package io.fundic.fundic_server.application.agent.stock;

import io.fundic.fundic_server.presentation.dto.UserProfileRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StockSelectionAgent 통합 테스트
 * Stub Provider와의 통합 동작 확인
 */
@SpringBootTest
@ActiveProfiles("local")
class StockSelectionAgentIntegrationTest {

    @Autowired
    private StockSelectionAgent stockSelectionAgent;

    @Test
    void testStockSelection() {
        // Given
        UserProfileRequest userProfile = new UserProfileRequest(
                "장기",    // horizon
                "중립",    // risk
                "배당",    // goal
                List.of("반도체", "AI")  // keywords
        );

        StockSelectionInput input = new StockSelectionInput(
                "1",                              // sectorId
                "반도체",                         // sectorName
                List.of("005930", "000660", "035420", "051910"),  // 삼성전자, SK하이닉스, NAVER, LG화학
                userProfile
        );

        // When
        StockSelectionOutput output = stockSelectionAgent.execute(input);

        // Then
        assertThat(output).isNotNull();
        assertThat(output.selectedStocks()).isNotEmpty();
        assertThat(output.selectedStocks().size()).isLessThanOrEqualTo(4);

        // 각 종목이 필수 정보를 가지고 있는지 확인
        output.selectedStocks().forEach(stock -> {
            assertThat(stock.stockCode()).isNotBlank();
            assertThat(stock.stockName()).isNotBlank();
            assertThat(stock.score()).isGreaterThan(0);
            assertThat(stock.reason()).isNotBlank();
        });

        // 로그 출력
        System.out.println("=== Stock Selection Result ===");
        output.selectedStocks().forEach(stock -> {
            System.out.printf("[%s] %s (점수: %d)%n", stock.stockCode(), stock.stockName(), stock.score());
            System.out.printf("  추천 이유: %s%n", stock.reason());
            if (stock.risks() != null && !stock.risks().isBlank()) {
                System.out.printf("  리스크: %s%n", stock.risks());
            }
            System.out.println();
        });
    }
}
