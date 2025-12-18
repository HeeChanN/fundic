package io.fundic.fundic_server.application.agent.portfolio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.fundic.fundic_server.application.agent.stock.StockSelectionOutput;
import io.fundic.fundic_server.config.GoogleApiConfig;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 포트폴리오 최적화 Agent
 * 3개 섹터와 선정된 종목들을 바탕으로 최적의 포트폴리오를 구성
 */
@Slf4j
@Component
public class PortfolioAgent {

    private final LlmAgent agent;
    private final InMemoryRunner runner;
    private final Session defaultSession;
    private final ObjectMapper objectMapper;

    public PortfolioAgent(ObjectMapper objectMapper, GoogleApiConfig googleApiConfig) {
        this.objectMapper = objectMapper;

        // Google API 키 확인 (GoogleApiConfig의 @PostConstruct에서 이미 환경 변수로 설정됨)
        if (!googleApiConfig.isConfigured()) {
            throw new IllegalStateException("Google API key is not configured. Please set 'google.api-key' in application.yml");
        }

        this.agent = LlmAgent.builder()
                .name("portfolio-agent")
                .model("gemini-2.0-flash-exp")
                .instruction(buildSystemPrompt())
                .build();

        this.runner = new InMemoryRunner(agent);

        // 기본 세션 생성
        this.defaultSession = runner
                .sessionService()
                .createSession(runner.appName(), "default-user")
                .blockingGet();

        log.info("PortfolioAgent initialized with session: {} using API key from config", defaultSession.id());
    }

    /**
     * 포트폴리오 최적화 실행
     *
     * @param input 포트폴리오 입력 데이터 (3개 섹터 + 종목 추천 결과)
     * @return 최적화된 포트폴리오 (섹터/종목 비중, 리밸런싱 룰, 설명)
     */
    public PortfolioOutput execute(PortfolioInput input) {
        log.info("Executing portfolio optimization for user profile: {}", input.userProfile());

        try {
            // 프롬프트 생성
            String prompt = buildPrompt(input);

            // Content 생성
            Content userContent = Content.fromParts(Part.fromText(prompt));

            // Agent 실행
            AtomicReference<String> responseText = new AtomicReference<>("");
            Flowable<Event> events = runner.runAsync(
                    defaultSession.userId(),
                    defaultSession.id(),
                    userContent
            );

            // 최종 응답 추출
            events.blockingForEach(event -> {
                if (event.finalResponse()) {
                    responseText.set(event.stringifyContent());
                    log.debug("Agent response: {}", responseText.get());
                }
            });

            // 응답을 파싱하여 PortfolioOutput으로 변환
            return parseResponse(responseText.get());

        } catch (Exception e) {
            log.error("Failed to execute portfolio optimization", e);
            throw new PortfolioOptimizationException("Failed to optimize portfolio", e);
        }
    }

    /**
     * Agent의 시스템 프롬프트 구성
     */
    private String buildSystemPrompt() {
        return """
                당신은 포트폴리오 매니저입니다.
                선정된 3개 섹터와 종목을 바탕으로 최적의 포트폴리오를 구성하세요.

                **역할**:
                - 리더 섹터: 포트폴리오의 주축 (기본 45%)
                - 서포트 섹터: 리더를 보완 (기본 35%)
                - 완충 섹터: 리스크 완화 (기본 20%)

                **출력 형식** (JSON):
                {
                  "sectorWeights": {
                    "leader": 45,
                    "support": 35,
                    "buffer": 20
                  },
                  "stockAllocations": [
                    {
                      "stockCode": "005930",
                      "stockName": "삼성전자",
                      "sector": "leader",
                      "weight": 15
                    }
                  ],
                  "rebalancingRules": [
                    "월 1회 리밸런싱 권장",
                    "리더 섹터 모멘텀 하락 시 완충 섹터 비중 +10%"
                  ],
                  "explanation": "이 포트폴리오는 반도체 업황 회복을 주축으로..."
                }

                IMPORTANT: 응답은 반드시 유효한 JSON 형식이어야 합니다.
                """;
    }

    /**
     * 사용자 프롬프트 구성
     */
    private String buildPrompt(PortfolioInput input) {
        return String.format("""
                **사용자 프로필**:
                - 투자 기간: %s
                - 리스크 성향: %s
                - 목표: %s

                **선정된 섹터 및 종목**:

                1. **리더 섹터**: %s (기본 비중 45%%)
                %s

                2. **서포트 섹터**: %s (기본 비중 35%%)
                %s

                3. **완충 섹터**: %s (기본 비중 20%%)
                %s

                **요구사항**:
                1. 섹터 비중 조정 (기본값에서 ±5%% 범위 내, 사용자 성향 고려)
                2. 각 섹터 내 종목 비중 배분 (섹터 내에서 균등 또는 가중)
                3. 리밸런싱 룰 제안 (주기, 트리거 조건)
                4. 포트폴리오 전체 설명 (시너지 효과, 기대 수익, 주의사항)

                위 정보를 바탕으로 최적의 포트폴리오를 구성하고 JSON 형식으로 응답해주세요.
                """,
                input.userProfile().getHorizon(),
                input.userProfile().getRisk(),
                input.userProfile().getGoal(),
                input.leaderSector().sectorName(),
                formatStockSelection(input.leaderSector().stockSelection()),
                input.supportSector().sectorName(),
                formatStockSelection(input.supportSector().stockSelection()),
                input.bufferSector().sectorName(),
                formatStockSelection(input.bufferSector().stockSelection())
        );
    }

    /**
     * 종목 선택 결과 포맷팅
     */
    private String formatStockSelection(StockSelectionOutput stockSelection) {
        if (stockSelection == null || stockSelection.selectedStocks().isEmpty()) {
            return "   (종목 없음)";
        }

        return stockSelection.selectedStocks().stream()
                .map(stock -> String.format("   - %s (%s): 점수 %d점\n     이유: %s",
                        stock.stockName(),
                        stock.stockCode(),
                        stock.score(),
                        stock.reason()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Agent 응답을 PortfolioOutput으로 파싱
     */
    private PortfolioOutput parseResponse(String response) {
        try {
            // JSON 추출
            String jsonString = extractJson(response);
            log.debug("Extracted JSON: {}", jsonString);

            // JSON 파싱
            JsonNode root = objectMapper.readTree(jsonString);

            // 섹터 비중 파싱
            JsonNode weightsNode = root.get("sectorWeights");
            if (weightsNode == null) {
                throw new IllegalArgumentException("Missing 'sectorWeights' in response");
            }

            PortfolioOutput.SectorWeights sectorWeights = new PortfolioOutput.SectorWeights(
                    weightsNode.get("leader").asDouble(),
                    weightsNode.get("support").asDouble(),
                    weightsNode.get("buffer").asDouble()
            );

            // 종목 배분 파싱
            List<PortfolioOutput.StockAllocation> stockAllocations = new ArrayList<>();
            JsonNode allocationsNode = root.get("stockAllocations");
            if (allocationsNode != null && allocationsNode.isArray()) {
                for (JsonNode allocationNode : allocationsNode) {
                    stockAllocations.add(new PortfolioOutput.StockAllocation(
                            allocationNode.get("stockCode").asText(),
                            allocationNode.get("stockName").asText(),
                            allocationNode.get("sector").asText(),
                            allocationNode.get("weight").asDouble()
                    ));
                }
            }

            // 리밸런싱 룰 파싱
            List<String> rebalancingRules = new ArrayList<>();
            JsonNode rulesNode = root.get("rebalancingRules");
            if (rulesNode != null && rulesNode.isArray()) {
                for (JsonNode ruleNode : rulesNode) {
                    rebalancingRules.add(ruleNode.asText());
                }
            }

            // 설명 파싱
            String explanation = root.has("explanation")
                    ? root.get("explanation").asText()
                    : "";

            log.info("Parsed portfolio: {} stocks, {} rules",
                    stockAllocations.size(), rebalancingRules.size());

            return new PortfolioOutput(
                    sectorWeights,
                    stockAllocations,
                    rebalancingRules,
                    explanation
            );

        } catch (JsonProcessingException e) {
            log.error("Failed to parse Agent JSON response", e);
            log.error("Raw response: {}", response);
            throw new PortfolioOptimizationException("Failed to parse portfolio response", e);
        }
    }

    /**
     * 응답에서 JSON 부분만 추출
     */
    private String extractJson(String response) {
        if (response == null || response.isBlank()) {
            return "{}";
        }

        String cleaned = response.trim();

        // 마크다운 코드 블록 제거
        if (cleaned.startsWith("```json")) {
            int start = cleaned.indexOf('\n') + 1;
            int end = cleaned.lastIndexOf("```");
            if (end > start) {
                cleaned = cleaned.substring(start, end).trim();
            }
        } else if (cleaned.startsWith("```")) {
            int start = cleaned.indexOf('\n') + 1;
            int end = cleaned.lastIndexOf("```");
            if (end > start) {
                cleaned = cleaned.substring(start, end).trim();
            }
        }

        // JSON 객체 찾기
        int jsonStart = cleaned.indexOf('{');
        if (jsonStart >= 0) {
            cleaned = cleaned.substring(jsonStart);
        }

        return cleaned;
    }

    public static class PortfolioOptimizationException extends RuntimeException {
        public PortfolioOptimizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
