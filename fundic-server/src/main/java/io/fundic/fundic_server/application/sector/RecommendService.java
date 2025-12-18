package io.fundic.fundic_server.application.sector;

import io.fundic.fundic_server.application.SectorConstituentsProvider;
import io.fundic.fundic_server.application.SectorNameProvider;
import io.fundic.fundic_server.application.StockPickProvider;
import io.fundic.fundic_server.application.agent.portfolio.PortfolioAgent;
import io.fundic.fundic_server.application.agent.portfolio.PortfolioInput;
import io.fundic.fundic_server.application.agent.portfolio.PortfolioOutput;
import io.fundic.fundic_server.application.agent.sector.SectorSelectionAgent;
import io.fundic.fundic_server.application.agent.sector.SectorSelectionInput;
import io.fundic.fundic_server.application.agent.sector.SectorSelectionOutput;
import io.fundic.fundic_server.application.agent.stock.StockSelectionAgent;
import io.fundic.fundic_server.application.agent.stock.StockSelectionInput;
import io.fundic.fundic_server.application.agent.stock.StockSelectionOutput;
import io.fundic.fundic_server.domain.SectorScore;
import io.fundic.fundic_server.domain.SectorSnapshot;
import io.fundic.fundic_server.domain.SectorSnapshotProvider;
import io.fundic.fundic_server.presentation.dto.PortfolioRecommendResDto;
import io.fundic.fundic_server.presentation.dto.SectorRecommendationResDto;
import io.fundic.fundic_server.presentation.dto.UserProfileRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {

    private final SectorSnapshotProvider sectorSnapshotProvider;
    private final SectorScoringService scoringService;
    private final SectorAllocationService allocationService;

    private final SectorDiscoveryService sectorDiscoveryService;
    private final StockPickProvider stockPickProvider;

    // AI Agents
    private final SectorSelectionAgent sectorSelectionAgent;
    private final StockSelectionAgent stockSelectionAgent;
    private final PortfolioAgent portfolioAgent;

    // Utilities
    private final SectorConstituentsProvider constituentsProvider;
    private final SectorNameProvider sectorNameProvider;


    public SectorRecommendationResDto recommendSectors(UserProfileRequest req) {
        log.info("Starting AI-based sector recommendation for user profile: {}", req);

        try {
            // 1. 섹터 유니버스 로드
            List<SectorSnapshot> universe = sectorSnapshotProvider.loadUniverse();

            // 2. 후보 섹터 필터링 (키워드 기반, 부족하면 전체로 fallback)
            List<SectorSnapshot> candidates = sectorDiscoveryService.filterCandidates(universe, req);

            log.info("Filtered {} candidate sectors from {} universe sectors",
                    candidates.size(), universe.size());

            // 3. AI Agent를 통한 섹터 선택
            SectorSelectionInput input = new SectorSelectionInput(candidates, req);
            SectorSelectionOutput agentOutput = sectorSelectionAgent.execute(input);

            // 4. Agent 출력을 DTO로 변환
            List<SectorRecommendationResDto.SectorCard> cards = List.of(
                    buildCardFromAgent("LEADER", agentOutput.leader(), "리더 섹터(추세 엔진)"),
                    buildCardFromAgent("SUPPORT", agentOutput.support(), "서브 섹터(보조 엔진)"),
                    buildCardFromAgent("BUFFER", agentOutput.buffer(), "완충 섹터(안전장치)")
            );

            // 5. 대체 옵션 계산 (기존 로직 재활용)
            List<SectorScore> scores = scoringService.score(candidates, req);
            var sel = new SectorAllocationService.Selection(
                    agentOutput.leader().sectorId(),
                    agentOutput.support().sectorId(),
                    agentOutput.buffer().sectorId()
            );
            Map<String, List<String>> swaps = allocationService.swapOptions(scores, candidates, sel);

            return SectorRecommendationResDto.builder()
                    .selected(SectorRecommendationResDto.SelectedSectors.builder()
                            .leader(agentOutput.leader().sectorId())
                            .support(agentOutput.support().sectorId())
                            .buffer(agentOutput.buffer().sectorId())
                            .build())
                    .swapOptions(SectorRecommendationResDto.SwapOptions.builder()
                            .leader(swaps.get("leader"))
                            .support(swaps.get("support"))
                            .buffer(swaps.get("buffer"))
                            .build())
                    .cards(cards)
                    .meta(Map.of(
                            "universeSize", universe.size(),
                            "candidateSize", candidates.size(),
                            "mode", "AI_AGENT",
                            "explanation", agentOutput.explanation()
                    ))
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate AI-based sector recommendation, falling back to rule-based", e);
            return fallbackSectorRecommendation(req);
        }
    }

    /**
     * Fallback: AI Agent 실패 시 기존 rule-based 로직 사용
     */
    private SectorRecommendationResDto fallbackSectorRecommendation(UserProfileRequest req) {
        log.warn("Using fallback rule-based sector recommendation");

        List<SectorSnapshot> universe = sectorSnapshotProvider.loadUniverse();
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
                .meta(Map.of(
                        "universeSize", universe.size(),
                        "candidateSize", candidates.size(),
                        "mode", "FALLBACK_RULE_BASED"
                ))
                .build();
    }


    public PortfolioRecommendResDto recommendPortfolio(UserProfileRequest req) {
        log.info("Starting AI-based portfolio recommendation for user profile: {}", req);

        try {
            // 1. 섹터 추천
            SectorRecommendationResDto sectorReco = recommendSectors(req);
            String leaderSectorId = sectorReco.getSelected().getLeader();
            String supportSectorId = sectorReco.getSelected().getSupport();
            String bufferSectorId = sectorReco.getSelected().getBuffer();

            // 2. 각 섹터별 종목 선택 (StockSelectionAgent)
            StockSelectionOutput leaderStocks = selectStocksForSector(leaderSectorId, req);
            StockSelectionOutput supportStocks = selectStocksForSector(supportSectorId, req);
            StockSelectionOutput bufferStocks = selectStocksForSector(bufferSectorId, req);

            // 3. 포트폴리오 최적화 (PortfolioAgent)
            PortfolioInput portfolioInput = new PortfolioInput(
                    new PortfolioInput.SectorInfo(leaderSectorId,
                            sectorNameProvider.findNameById(leaderSectorId).orElse("섹터" + leaderSectorId),
                            leaderStocks),
                    new PortfolioInput.SectorInfo(supportSectorId,
                            sectorNameProvider.findNameById(supportSectorId).orElse("섹터" + supportSectorId),
                            supportStocks),
                    new PortfolioInput.SectorInfo(bufferSectorId,
                            sectorNameProvider.findNameById(bufferSectorId).orElse("섹터" + bufferSectorId),
                            bufferStocks),
                    req
            );

            PortfolioOutput portfolioOutput = portfolioAgent.execute(portfolioInput);

            // 4. PortfolioRecommendResDto로 변환
            return convertToResponseDto(portfolioOutput, leaderSectorId, supportSectorId, bufferSectorId);

        } catch (Exception e) {
            log.error("Failed to generate AI-based portfolio, falling back to rule-based", e);
            return fallbackPortfolio(req);
        }
    }

    /**
     * 특정 섹터에 대한 종목 선택 실행
     */
    private StockSelectionOutput selectStocksForSector(String sectorId, UserProfileRequest profile) {
        List<String> stockCodes = constituentsProvider.getStockCodesBySector(sectorId);
        String sectorName = sectorNameProvider.findNameById(sectorId).orElse("섹터" + sectorId);

        StockSelectionInput input = new StockSelectionInput(
                Long.parseLong(sectorId),
                sectorName,
                stockCodes,
                profile
        );

        return stockSelectionAgent.execute(input);
    }

    /**
     * PortfolioOutput을 PortfolioRecommendResDto로 변환
     */
    private PortfolioRecommendResDto convertToResponseDto(
            PortfolioOutput portfolioOutput,
            String leaderSectorId,
            String supportSectorId,
            String bufferSectorId
    ) {
        // 섹터 비중 변환
        Map<String, Integer> sectorWeights = new LinkedHashMap<>();
        sectorWeights.put(leaderSectorId, (int) portfolioOutput.sectorWeights().leader());
        sectorWeights.put(supportSectorId, (int) portfolioOutput.sectorWeights().support());
        sectorWeights.put(bufferSectorId, (int) portfolioOutput.sectorWeights().buffer());

        // 종목별 비중을 섹터별로 그룹화
        Map<String, List<PortfolioRecommendResDto.StockPick>> stockPicksBySector = new LinkedHashMap<>();

        Map<String, List<PortfolioOutput.StockAllocation>> bySector = portfolioOutput.stockAllocations().stream()
                .collect(Collectors.groupingBy(PortfolioOutput.StockAllocation::sector));

        for (Map.Entry<String, List<PortfolioOutput.StockAllocation>> entry : bySector.entrySet()) {
            String sectorRole = entry.getKey();  // "leader", "support", "buffer"
            String sectorId = getSectorIdByRole(sectorRole, leaderSectorId, supportSectorId, bufferSectorId);

            List<PortfolioRecommendResDto.StockPick> picks = entry.getValue().stream()
                    .map(alloc -> PortfolioRecommendResDto.StockPick.builder()
                            .code(alloc.stockCode())
                            .name(alloc.stockName())
                            .score((int) (alloc.weight() * 10))  // 비중을 점수로 근사
                            .reasons(List.of(String.format("포트폴리오 비중: %.1f%%", alloc.weight())))
                            .build())
                    .toList();

            stockPicksBySector.put(sectorId, picks);
        }

        return PortfolioRecommendResDto.builder()
                .sectorWeights(sectorWeights)
                .stockPicksBySector(stockPicksBySector)
                .rebalancingRules(portfolioOutput.rebalancingRules())
                .caution("본 결과는 AI 기반 정보 제공 목적이며, 시장 상황에 따라 손실이 발생할 수 있습니다. " +
                        portfolioOutput.explanation())
                .meta(Map.of("mode", "AI_AGENT"))
                .build();
    }

    /**
     * 섹터 역할(leader/support/buffer)로부터 실제 섹터 ID 반환
     */
    private String getSectorIdByRole(String role, String leaderSectorId, String supportSectorId, String bufferSectorId) {
        return switch (role.toLowerCase()) {
            case "leader" -> leaderSectorId;
            case "support" -> supportSectorId;
            case "buffer" -> bufferSectorId;
            default -> leaderSectorId;
        };
    }

    /**
     * Fallback: AI Agent 실패 시 기존 rule-based 로직 사용
     */
    private PortfolioRecommendResDto fallbackPortfolio(UserProfileRequest req) {
        log.warn("Using fallback rule-based portfolio");

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
                .meta(Map.of("mode", "FALLBACK_RULE_BASED"))
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

    /**
     * Agent 출력으로부터 SectorCard 생성
     */
    private SectorRecommendationResDto.SectorCard buildCardFromAgent(
            String role,
            SectorSelectionOutput.SelectedSector sector,
            String title
    ) {
        List<SectorRecommendationResDto.Kpi> kpis = List.of(
                kpi("AI 점수", String.valueOf(sector.score())),
                kpi("섹터 ID", sector.sectorId())
        );

        String caution = switch (role) {
            case "LEADER" -> "단기 과열 시 조정 가능성에 유의하세요.";
            case "SUPPORT" -> "섹터/업황 이벤트에 따라 민감하게 움직일 수 있습니다.";
            case "BUFFER" -> "상승장에서는 상대 성과가 낮을 수 있습니다.";
            default -> "시장 상황에 따라 변동될 수 있습니다.";
        };

        return SectorRecommendationResDto.SectorCard.builder()
                .role(role)
                .sectorId(sector.sectorId())
                .title(title)
                .oneLineReason(sector.reason())
                .kpis(kpis)
                .caution(caution)
                .build();
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