package io.fundic.fundic_server.application.sector;

import io.fundic.fundic_server.application.StockPickProvider;
import io.fundic.fundic_server.domain.SectorScore;
import io.fundic.fundic_server.domain.SectorSnapshot;
import io.fundic.fundic_server.domain.SectorSnapshotProvider;
import io.fundic.fundic_server.presentation.dto.PortfolioRecommendResDto;
import io.fundic.fundic_server.presentation.dto.SectorRecommendationResDto;
import io.fundic.fundic_server.presentation.dto.UserProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecommendService {

    private final SectorSnapshotProvider sectorSnapshotProvider;
    private final SectorScoringService scoringService;
    private final SectorAllocationService allocationService;

    private final SectorDiscoveryService sectorDiscoveryService;
    private final StockPickProvider stockPickProvider;


    public SectorRecommendationResDto recommendSectors(UserProfileRequest req) {
        List<SectorSnapshot> universe = sectorSnapshotProvider.loadUniverse();

        // ✅ 후보 섹터 필터링(키워드 기반, 부족하면 전체로 fallback)
        List<SectorSnapshot> candidates = sectorDiscoveryService.filterCandidates(universe, req);

        List<SectorScore> scores = scoringService.score(candidates, req);
        var sel = allocationService.allocate(scores, candidates);
        Map<String, List<String>> swaps = allocationService.swapOptions(scores, candidates, sel);

        List<SectorRecommendationResDto.SectorCard> cards = List.of(
                buildCard("LEADER", sel.leader(), "리더 섹터(추세 엔진)", scores),
                buildCard("SUPPORT", sel.support(), "서브 섹터(보조 엔진)", scores),
                buildCard("BUFFER", sel.buffer(), "완충 섹터(안전장치)", scores)
        );

        return SectorRecommendationResDto.builder()
                .selected(SectorRecommendationResDto.SelectedSectors.builder()
                        .leader(sel.leader()).support(sel.support()).buffer(sel.buffer()).build())
                .swapOptions(SectorRecommendationResDto.SwapOptions.builder()
                        .leader(swaps.get("leader")).support(swaps.get("support")).buffer(swaps.get("buffer")).build())
                .cards(cards)
                .meta(Map.of("universeSize", universe.size(), "candidateSize", candidates.size()))
                .build();
    }


    public PortfolioRecommendResDto recommendPortfolio(UserProfileRequest req) {
        SectorRecommendationResDto sectorReco = recommendSectors(req);

        Map<String, Integer> sectorWeights = sectorWeights(req.getRisk(),
                sectorReco.getSelected().getLeader(),
                sectorReco.getSelected().getSupport(),
                sectorReco.getSelected().getBuffer()
        );

        Map<String, List<PortfolioRecommendResDto.StockPick>> picks = new LinkedHashMap<>();
        for (String sectorId : sectorWeights.keySet()) {
            picks.put(sectorId, stockPickProvider.pickTopStocks(sectorId, req));
        }

        List<String> rules = List.of(
                "월 1회 정기 리밸런싱(추천)",
                "리더 섹터 추세 붕괴 시 리더 비중 10% 축소 → 완충/현금으로 이동",
                "종목 최대낙폭 기준 초과 시 전량 손절 대신 1단계(부분) 축소"
        );

        return PortfolioRecommendResDto.builder()
                .sectorWeights(sectorWeights)
                .stockPicksBySector(picks)
                .rebalancingRules(rules)
                .caution("본 결과는 정보 제공 목적이며, 시장 상황에 따라 손실이 발생할 수 있습니다.")
                .meta(Map.of("mode", "MVP_PROVIDER"))
                .build();
    }

    private Map<String, Integer> sectorWeights(UserProfileRequest.Risk risk, String leader, String support, String buffer) {
        int l, s, b;
        switch (risk) {
            case HIGH -> { l = 45; s = 35; b = 20; }
            case MEDIUM -> { l = 40; s = 35; b = 25; }
            case LOW -> { l = 30; s = 30; b = 40; }
            default -> { l = 40; s = 35; b = 25; }
        }
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put(leader, l);
        m.put(support, s);
        m.put(buffer, b);
        return m;
    }

    private SectorRecommendationResDto.SectorCard buildCard(String role, String sectorId, String title, List<SectorScore> scores) {
        SectorScore sc = scores.stream().filter(s -> s.getSectorId().equals(sectorId)).findFirst().orElse(null);

        String oneLine;
        String caution;
        List<SectorRecommendationResDto.Kpi> kpis;

        if ("LEADER".equals(role)) {
            oneLine = "최근 모멘텀/추세가 상대적으로 강한 섹터입니다.";
            caution = "단기 과열 시 조정 가능성에 유의하세요.";
            kpis = List.of(
                    kpi("모멘텀 점수", sc == null ? "-" : String.valueOf(sc.getMomentumScore())),
                    kpi("종합 점수", sc == null ? "-" : String.valueOf(sc.getTotalScore()))
            );
        } else if ("SUPPORT".equals(role)) {
            oneLine = "리더와 함께 수익 기여를 기대하면서 분산에도 도움되는 섹터입니다.";
            caution = "섹터/업황 이벤트에 따라 민감하게 움직일 수 있습니다.";
            kpis = List.of(
                    kpi("종합 점수", sc == null ? "-" : String.valueOf(sc.getTotalScore())),
                    kpi("이벤트 점수", sc == null ? "-" : String.valueOf(sc.getEventScore()))
            );
        } else {
            oneLine = "변동성/낙폭이 상대적으로 낮아 포트폴리오 완충 역할을 합니다.";
            caution = "상승장에서는 상대 성과가 낮을 수 있습니다.";
            kpis = List.of(
                    kpi("안정 점수", sc == null ? "-" : String.valueOf(sc.getRiskScore())),
                    kpi("종합 점수", sc == null ? "-" : String.valueOf(sc.getTotalScore()))
            );
        }

        return SectorRecommendationResDto.SectorCard.builder()
                .role(role)
                .sectorId(sectorId)
                .title(title)
                .oneLineReason(oneLine)
                .kpis(kpis)
                .caution(caution)
                .build();
    }

    private SectorRecommendationResDto.Kpi kpi(String label, String value) {
        return SectorRecommendationResDto.Kpi.builder().label(label).value(value).build();
    }
//
//    private List<PortfolioRecommendResDto.StockPick> stubPicks(String sectorId) {
//        // TODO: 실제 종목 분석(가격/재무/뉴스)로 교체
//        return List.of(
//                PortfolioRecommendResDto.StockPick.builder()
//                        .code(sectorId + "_A")
//                        .name(sectorId + " 대표주A")
//                        .score(75)
//                        .reasons(List.of("추세 안정", "뉴스 리스크 낮음"))
//                        .build(),
//                PortfolioRecommendResDto.StockPick.builder()
//                        .code(sectorId + "_B")
//                        .name(sectorId + " 대표주B")
//                        .score(70)
//                        .reasons(List.of("상대강도 양호", "재무 지표 무난"))
//                        .build()
//        );
//    }
}