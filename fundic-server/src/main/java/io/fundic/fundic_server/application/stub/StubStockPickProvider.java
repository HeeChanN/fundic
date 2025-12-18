package io.fundic.fundic_server.application.stub;

import io.fundic.fundic_server.application.StockPickProvider;
import io.fundic.fundic_server.presentation.dto.PortfolioRecommendResDto;
import io.fundic.fundic_server.presentation.dto.UserProfileRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("stub")
public class StubStockPickProvider implements StockPickProvider {

    @Override
    public List<PortfolioRecommendResDto.StockPick> pickTopStocks(String sectorId, UserProfileRequest profile) {
        // TODO: 실제 로직으로 교체(가격/재무/뉴스)
        return List.of(
                PortfolioRecommendResDto.StockPick.builder()
                        .code(sectorId + "_A")
                        .name(sectorId + " 대표주A")
                        .score(75)
                        .reasons(List.of("추세 안정", "뉴스 리스크 낮음"))
                        .build(),
                PortfolioRecommendResDto.StockPick.builder()
                        .code(sectorId + "_B")
                        .name(sectorId + " 대표주B")
                        .score(70)
                        .reasons(List.of("상대강도 양호", "재무 지표 무난"))
                        .build()
        );
    }
}