package io.fundic.fundic_server.application.agent.stock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.fundic.fundic_server.application.FinancialDataProvider;
import io.fundic.fundic_server.application.StockDataProvider;
import io.fundic.fundic_server.config.GoogleApiConfig;
import io.fundic.fundic_server.domain.FinancialData;
import io.fundic.fundic_server.domain.StockData;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 종목 선택 Agent
 * 섹터 내에서 사용자 프로필에 맞는 Top 4 종목을 선정하고 추천 이유를 제공
 */
@Slf4j
@Component
public class StockSelectionAgent {

    private final LlmAgent agent;
    private final InMemoryRunner runner;
    private final Session defaultSession;
    private final ObjectMapper objectMapper;
    private final StockDataProvider stockDataProvider;
    private final FinancialDataProvider financialDataProvider;

    public StockSelectionAgent(
            ObjectMapper objectMapper,
            StockDataProvider stockDataProvider,
            FinancialDataProvider financialDataProvider,
            GoogleApiConfig googleApiConfig
    ) {
        this.objectMapper = objectMapper;
        this.stockDataProvider = stockDataProvider;
        this.financialDataProvider = financialDataProvider;

        // Google API 키 확인 (GoogleApiConfig의 @PostConstruct에서 이미 환경 변수로 설정됨)
        if (!googleApiConfig.isConfigured()) {
            throw new IllegalStateException("Google API key is not configured. Please set 'google.api-key' in application.yml");
        }

        this.agent = LlmAgent.builder()
                .name("stock-selection-agent")
                .model("gemini-2.0-flash-exp")
                .instruction(buildSystemPrompt())
                .build();

        this.runner = new InMemoryRunner(agent);

        // 기본 세션 생성
        this.defaultSession = runner
                .sessionService()
                .createSession(runner.appName(), "default-user")
                .blockingGet();

        log.info("StockSelectionAgent initialized with session: {} using API key from config", defaultSession.id());
    }

    /**
     * 종목 선택 실행
     *
     * @param input 종목 선택 입력 데이터
     * @return 선택된 종목 목록과 추천 이유
     */
    public StockSelectionOutput execute(StockSelectionInput input) {
        log.info("Executing stock selection for sector: {}, userProfile: {}",
                input.sectorId(), input.userProfile());

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

            // 응답을 파싱하여 StockSelectionOutput으로 변환
            return parseResponse(responseText.get(), input);

        } catch (Exception e) {
            log.error("Failed to execute stock selection", e);
            throw new StockSelectionException("Failed to select stocks", e);
        }
    }

    /**
     * Agent의 시스템 프롬프트 구성
     */
    private String buildSystemPrompt() {
        return """
                당신은 한국 증시 전문 애널리스트입니다.
                주어진 섹터 내에서 사용자의 투자 성향에 맞는 종목을 선정하고,
                각 종목의 추천 이유를 명확하게 설명하세요.

                **출력 형식** (JSON):
                {
                  "selectedStocks": [
                    {
                      "stockCode": "005930",
                      "stockName": "삼성전자",
                      "score": 85,
                      "reason": "반도체 업황 회복 기대감과 함께 RSI가 상승 추세...",
                      "risks": "미중 무역 분쟁 재개 시 수출 감소 우려"
                    }
                  ]
                }

                IMPORTANT: 응답은 반드시 유효한 JSON 형식이어야 합니다.
                """;
    }

    /**
     * 사용자 프롬프트 구성
     */
    private String buildPrompt(StockSelectionInput input) {
        // 종목 데이터 조회
        Map<String, StockData> stockDataMap = stockDataProvider.getStockDataBatch(input.stockCodes());
        Map<String, FinancialData> financialDataMap = financialDataProvider.getFinancialDataBatch(input.stockCodes());

        // 종목 정보 포맷팅
        String stocksInfo = input.stockCodes().stream()
                .map(code -> formatStockInfo(code, stockDataMap.get(code), financialDataMap.get(code)))
                .collect(Collectors.joining("\n\n"));

        return String.format("""
                **사용자 프로필**:
                - 투자 기간: %s
                - 리스크 성향: %s
                - 목표: %s

                **섹터 정보**:
                - 섹터 ID: %s
                - 섹터명: %s

                **분석 대상 종목들**:
                %s

                위 정보를 바탕으로 Top 4 종목을 선정하고, 각 종목의 추천 이유와 리스크를 설명해주세요.
                """,
                input.userProfile().getHorizon(),
                input.userProfile().getRisk(),
                input.userProfile().getGoal(),
                input.sectorId(),
                input.sectorName(),
                stocksInfo
        );
    }

    /**
     * 개별 종목 정보 포맷팅
     */
    private String formatStockInfo(String stockCode, StockData stockData, FinancialData financialData) {
        if (stockData == null || financialData == null) {
            return String.format("- %s: 데이터 없음", stockCode);
        }

        return String.format("""
                - **%s (%s)**
                  가격: %,d원 (변동률: %.2f%%)
                  거래량: %,d주
                  기술적 지표: RSI=%.1f, MACD=%.2f
                  재무 지표: PER=%.2f, PBR=%.2f, ROE=%.2f%%, 영업이익률=%.2f%%
                  시가총액: %,d백만원
                """,
                stockData.stockName(),
                stockCode,
                stockData.currentPrice(),
                stockData.changeRate(),
                stockData.volume(),
                stockData.technical().rsi(),
                stockData.technical().macd(),
                financialData.per(),
                financialData.pbr(),
                financialData.roe(),
                financialData.operatingMargin(),
                financialData.marketCap()
        );
    }

    /**
     * Agent 응답을 StockSelectionOutput으로 파싱
     */
    private StockSelectionOutput parseResponse(String response, StockSelectionInput input) {
        try {
            // JSON 추출 (Agent가 마크다운 코드 블록으로 감쌀 수 있음)
            String jsonString = extractJson(response);

            log.debug("Extracted JSON: {}", jsonString);

            // JSON 파싱
            JsonNode root = objectMapper.readTree(jsonString);
            JsonNode selectedStocksNode = root.get("selectedStocks");

            if (selectedStocksNode == null || !selectedStocksNode.isArray()) {
                log.warn("Invalid response format: missing 'selectedStocks' array");
                return new StockSelectionOutput(List.of());
            }

            // SelectedStock 리스트 생성
            List<StockSelectionOutput.SelectedStock> stocks = new ArrayList<>();
            for (JsonNode stockNode : selectedStocksNode) {
                stocks.add(new StockSelectionOutput.SelectedStock(
                        stockNode.get("stockCode").asText(),
                        stockNode.get("stockName").asText(),
                        stockNode.get("score").asInt(),
                        stockNode.get("reason").asText(),
                        stockNode.has("risks") ? stockNode.get("risks").asText() : ""
                ));
            }

            log.info("Parsed {} stocks from Agent response", stocks.size());
            return new StockSelectionOutput(stocks);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse Agent JSON response", e);
            log.error("Raw response: {}", response);
            // 파싱 실패 시 빈 결과 반환
            return new StockSelectionOutput(List.of());
        }
    }

    /**
     * 응답에서 JSON 부분만 추출
     * Agent가 ```json ... ``` 형식으로 응답할 수 있으므로 추출 필요
     */
    private String extractJson(String response) {
        if (response == null || response.isBlank()) {
            return "{}";
        }

        // 마크다운 코드 블록 제거
        String cleaned = response.trim();

        // ```json ... ``` 형식 처리
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

    public static class StockSelectionException extends RuntimeException {
        public StockSelectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
