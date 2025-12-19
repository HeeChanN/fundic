package io.fundic.fundic_server.presentation;

import io.fundic.fundic_server.application.sector.RecommendService;
import io.fundic.fundic_server.presentation.dto.PortfolioRecommendRequest;
import io.fundic.fundic_server.presentation.dto.PortfolioRecommendResDto;
import io.fundic.fundic_server.presentation.dto.SectorRecommendationResDto;
import io.fundic.fundic_server.presentation.dto.UserProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class RecommendController {

    private final RecommendService recommendService;

    /**
     * 섹터 추천 API
     * 사용자 프로필을 기반으로 Leader, Support, Buffer 섹터를 추천
     */
    @PostMapping("/recommend/sectors")
    public SectorRecommendationResDto recommendSectors(@RequestBody UserProfileRequest req) {
        return recommendService.recommendSectors(req);
    }

    /**
     * 포트폴리오 추천 API
     * 사용자가 선택한 섹터 ID 3개를 기반으로 포트폴리오를 구성
     */
    @PostMapping("/portfolio")
    public PortfolioRecommendResDto recommendPortfolio(@RequestBody PortfolioRecommendRequest req) {
        return recommendService.recommendPortfolio(
                req.getLeaderSectorId(),
                req.getSupportSectorId(),
                req.getBufferSectorId(),
                req.toUserProfileRequest()
        );
    }
}
