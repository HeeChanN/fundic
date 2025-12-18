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
import io.fundic.fundic_server.application.FinancialDataProvider;
import io.fundic.fundic_server.application.SectorConstituentsProvider;
import io.fundic.fundic_server.application.SectorNameProvider;
import io.fundic.fundic_server.application.StockDataProvider;
import io.fundic.fundic_server.config.GoogleApiConfig;
import io.fundic.fundic_server.domain.FinancialData;
import io.fundic.fundic_server.domain.Stock;
import io.fundic.fundic_server.domain.StockData;
import io.fundic.fundic_server.infrastructure.sector.StockRepository;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 포트폴리오 최적화 Agent
 * 섹터 ID를 받아 종목 선택 + 포트폴리오 최적화를 모두 수행
 */
@Slf4j
@Component
public class PortfolioAgent {

    private final LlmAgent agent;
    private final InMemoryRunner runner;
    private final Session defaultSession;
    private final ObjectMapper objectMapper;

    // 데이터 제공자
    private final StockDataProvider stockDataProvider;
    private final FinancialDataProvider financialDataProvider;
    private final SectorConstituentsProvider constituentsProvider;
    private final SectorNameProvider sectorNameProvider;
    private final StockRepository stockRepository;

    public PortfolioAgent(
            ObjectMapper objectMapper,
            StockDataProvider stockDataProvider,
            FinancialDataProvider financialDataProvider,
            SectorConstituentsProvider constituentsProvider,
            SectorNameProvider sectorNameProvider,
            StockRepository stockRepository,
            GoogleApiConfig googleApiConfig
    ) {
        this.objectMapper = objectMapper;
        this.stockDataProvider = stockDataProvider;
        this.financialDataProvider = financialDataProvider;
        this.constituentsProvider = constituentsProvider;
        this.sectorNameProvider = sectorNameProvider;
        this.stockRepository = stockRepository;

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
                주어진 3개 섹터의 종목 데이터를 분석하여:
                1. 전체 포트폴리오에서 Top 10 종목 선정 (각 섹터에서 고르게 선정)
                2. 섹터 비중 최적화
                3. 종목별 비중 배분
                4. 리밸런싱 룰 제안

                **섹터 역할**:
                - 리더 섹터: 포트폴리오의 주축 (기본 45%, 사용자 리스크에 따라 ±5% 조정 가능)
                - 서포트 섹터: 리더를 보완 (기본 35%)
                - 완충 섹터: 리스크 완화 (기본 20%, 보수적 성향일수록 높임)

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
                      "weight": 15.0
                    }
                  ],
                  "rebalancingRules": [
                    "월 1회 리밸런싱 권장",
                    "리더 섹터 모멘텀 하락 시 완충 섹터 비중 +10%"
                  ],
                  "explanation": "이 포트폴리오는 반도체 업황 회복을 주축으로..."
                }

                IMPORTANT:
                - 응답은 반드시 유효한 JSON 형식이어야 합니다.
                - **전체 포트폴리오에서 정확히 10개 종목을 선정**하세요.
                - 각 섹터에서 고르게 선정하되, 섹터 비중에 맞춰 조정 가능 (예: 리더 5개, 서포트 3개, 완충 2개)
                - 섹터 비중의 합은 100%가 되어야 합니다.
                - 종목 비중의 합도 100%가 되어야 합니다.
                - 각 종목의 비중은 해당 섹터 비중을 초과할 수 없습니다.
                """;
    }

    /**
     * 사용자 프롬프트 구성
     */
    private String buildPrompt(PortfolioInput input) {
        // 각 섹터의 종목 데이터 조회
        String leaderName = sectorNameProvider.findNameById(input.leaderSectorId())
                .orElse("섹터" + input.leaderSectorId());
        String supportName = sectorNameProvider.findNameById(input.supportSectorId())
                .orElse("섹터" + input.supportSectorId());
        String bufferName = sectorNameProvider.findNameById(input.bufferSectorId())
                .orElse("섹터" + input.bufferSectorId());

        return String.format("""
                **사용자 프로필**:
                - 투자 기간: %s
                - 리스크 성향: %s
                - 목표: %s

                **섹터 정보 및 종목 데이터**:

                1. **리더 섹터**: %s (ID: %s, 기본 비중 45%%)
                %s

                2. **서포트 섹터**: %s (ID: %s, 기본 비중 35%%)
                %s

                3. **완충 섹터**: %s (ID: %s, 기본 비중 20%%)
                %s

                **요구사항**:
                1. 각 섹터에서 사용자 프로필에 맞는 Top 4 종목 선정
                2. 섹터 비중 조정 (기본값에서 ±5%% 범위 내, 사용자 성향 고려)
                3. 선정된 종목의 비중 배분 (섹터 내에서 균등 또는 가중)
                4. 리밸런싱 룰 제안 (주기, 트리거 조건)
                5. 포트폴리오 전체 설명 (종목 선정 이유, 시너지 효과, 기대 수익, 주의사항)

                위 정보를 바탕으로 최적의 포트폴리오를 구성하고 JSON 형식으로 응답해주세요.
                """,
                input.userProfile().getHorizon(),
                input.userProfile().getRisk(),
                input.userProfile().getGoal(),
                leaderName,
                input.leaderSectorId(),
                formatSectorStocks(input.leaderSectorId()),
                supportName,
                input.supportSectorId(),
                formatSectorStocks(input.supportSectorId()),
                bufferName,
                input.bufferSectorId(),
                formatSectorStocks(input.bufferSectorId())
        );
    }

    /**
     * 섹터의 종목 데이터 포맷팅 (DB 조회 사용)
     */
    private String formatSectorStocks(String sectorId) {
        // DB에서 섹터에 속한 종목 조회
        List<Stock> stocks = stockRepository.findBySectorId(Long.parseLong(sectorId));
        if (stocks.isEmpty()) {
            return "   (종목 없음)";
        }

        // 종목 코드 추출
        List<String> stockCodes = stocks.stream()
                .map(Stock::getCode)
                .limit(15)  // 너무 많으면 프롬프트가 길어지므로 상위 15개만
                .toList();

        // 종목 데이터 조회
        Map<String, StockData> stockDataMap = stockDataProvider.getStockDataBatch(stockCodes);
        Map<String, FinancialData> financialDataMap = financialDataProvider.getFinancialDataBatch(stockCodes);

        // 종목 정보 포맷팅
        return stocks.stream()
                .limit(15)
                .map(stock -> {
                    StockData stockData = stockDataMap.get(stock.getCode());
                    FinancialData financialData = financialDataMap.get(stock.getCode());

                    if (stockData == null || financialData == null) {
                        return String.format("   - %s (%s): 데이터 없음", stock.getName(), stock.getCode());
                    }

                    return String.format("""
                            - **%s (%s)** [%s]
                              가격: %,d원 (변동률: %.2f%%), 거래량: %,d주
                              기술적 지표: RSI=%.1f, MACD=%.2f
                              재무 지표: PER=%.2f, PBR=%.2f, ROE=%.2f%%
                            """,
                            stock.getName(),
                            stock.getCode(),
                            stock.getMarket(),
                            stockData.currentPrice(),
                            stockData.changeRate(),
                            stockData.volume(),
                            stockData.technical().rsi(),
                            stockData.technical().macd(),
                            financialData.per(),
                            financialData.pbr(),
                            financialData.roe());
                })
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
