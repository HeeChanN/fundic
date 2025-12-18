package io.fundic.fundic_server.presentation;

import io.fundic.fundic_server.application.sector.RecommendService;
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

    private final RecommendService recoFacadeService;

    @PostMapping("/recommend/sectors")
    public SectorRecommendationResDto recommendSectors(@RequestBody UserProfileRequest req) {
        return recoFacadeService.recommendSectors(req);
    }

    @PostMapping("/portfolio")
    public PortfolioRecommendResDto recommendPortfolio(@RequestBody UserProfileRequest req) {
        return recoFacadeService.recommendPortfolio(req);
    }
}
