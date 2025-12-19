package io.fundic.fundic_server.application.agent.sector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.models.Gemini;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.fundic.fundic_server.application.SectorNameProvider;
import io.fundic.fundic_server.domain.SectorSnapshot;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SectorSelectionAgent {

    private final LlmAgent agent;
    private final InMemoryRunner runner;
    private final Session defaultSession;
    private final ObjectMapper objectMapper;
    private final SectorNameProvider sectorNameProvider;

    public SectorSelectionAgent(
            ObjectMapper objectMapper,
            SectorNameProvider sectorNameProvider,
            Gemini geminiModel
    ) {
        this.objectMapper = objectMapper;
        this.sectorNameProvider = sectorNameProvider;

        this.agent = LlmAgent.builder()
                .name("sector-selection-agent")
                .model(geminiModel)          // ✅ 문자열 대신 Gemini 객체
                .instruction(buildSystemPrompt())
                .build();

        this.runner = new InMemoryRunner(agent);

        this.defaultSession = runner
                .sessionService()
                .createSession(runner.appName(), "default-user")
                .blockingGet();

        log.info("SectorSelectionAgent initialized with session: {}", defaultSession.id());
    }

    /**
     * 섹터 선택 실행
     *
     * @param input 섹터 선택 입력 데이터
     * @return 선택된 Leader, Support, Buffer 섹터와 추천 이유
     */
    public SectorSelectionOutput execute(SectorSelectionInput input) {
        log.info("Executing sector selection for {} candidates, userProfile: {}",
                input.candidateSectors().size(), input.userProfile());

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

            // 응답을 파싱하여 SectorSelectionOutput으로 변환
            return parseResponse(responseText.get());

        } catch (Exception e) {
            log.error("Failed to execute sector selection", e);
            throw new SectorSelectionException("Failed to select sectors", e);
        }
    }

    /**
     * Agent의 시스템 프롬프트 구성
     */
    private String buildSystemPrompt() {
        return """
                당신은 한국 증시 전문 포트폴리오 매니저입니다.
                주어진 섹터 후보들 중에서 사용자의 투자 성향에 맞는 3개의 섹터를 선정하세요.

                **섹터 역할**:
                - Leader: 모멘텀이 강하고 추세가 좋은 주도 섹터 (포트폴리오의 수익 엔진)
                - Support: Leader를 보완하며 분산 효과가 있는 지원 섹터
                - Buffer: 변동성이 낮고 안정적인 방어 섹터 (하락장 완충)

                **선택 기준**:
                - 사용자의 투자 기간, 리스크 성향, 목표를 고려
                - 섹터 간 상관관계를 고려하여 분산 효과 극대화
                - 각 섹터의 모멘텀, 리스크, 이벤트 지표 분석

                **출력 형식** (JSON):
                {
                  "leader": {
                    "sectorId": "1",
                    "sectorName": "반도체",
                    "score": 85,
                    "reason": "최근 3개월 수익률 12%로 강한 상승 추세. AI 수요 증가로 모멘텀 지속 기대"
                  },
                  "support": {
                    "sectorId": "2",
                    "sectorName": "2차전지",
                    "score": 78,
                    "reason": "전기차 보급 확대로 중장기 성장성 우수. Leader와 상관관계 낮아 분산 효과"
                  },
                  "buffer": {
                    "sectorId": "3",
                    "sectorName": "필수소비재",
                    "score": 72,
                    "reason": "변동성 낮고 안정적 현금흐름. 하락장에서 방어력 제공"
                  },
                  "explanation": "전반적인 포트폴리오 전략 및 섹터 조합의 시너지 설명"
                }

                IMPORTANT: 응답은 반드시 유효한 JSON 형식이어야 합니다.
                각 섹터는 서로 다른 섹터여야 하며, 중복되어서는 안 됩니다.
                """;
    }

    /**
     * 사용자 프롬프트 구성
     */
    private String buildPrompt(SectorSelectionInput input) {
        // 섹터 정보 포맷팅
        String sectorsInfo = input.candidateSectors().stream()
                .map(this::formatSectorInfo)
                .collect(Collectors.joining("\n\n"));

        return String.format("""
                **사용자 프로필**:
                - 투자 기간: %s
                - 리스크 성향: %s
                - 목표: %s

                **분석 대상 섹터들**:
                %s

                위 정보를 바탕으로 Leader, Support, Buffer 섹터를 선정하고,
                각 섹터의 선정 이유와 전체 포트폴리오 전략을 설명해주세요.
                """,
                input.userProfile().getHorizon(),
                input.userProfile().getRisk(),
                input.userProfile().getGoal(),
                sectorsInfo
        );
    }

    /**
     * 개별 섹터 정보 포맷팅
     */
    private String formatSectorInfo(SectorSnapshot sector) {
        String sectorName = sectorNameProvider.findNameById(sector.getSectorId())
                .orElse("섹터" + sector.getSectorId());

        return String.format("""
                - **%s (ID: %s)**
                  수익률: 1개월 %.2f%%, 3개월 %.2f%%
                  추세: %.2f (20일 기준)
                  변동성: %.2f%% (20일)
                  최대낙폭: %.2f%% (60일)
                  뉴스: 긍정 %d건, 부정 %d건
                """,
                sectorName,
                sector.getSectorId(),
                sector.getRet1m() * 100,
                sector.getRet3m() * 100,
                sector.getTrend20d(),
                sector.getVol20d() * 100,
                sector.getMdd60d() * 100,
                sector.getPosNews(),
                sector.getNegNews()
        );
    }

    /**
     * Agent 응답을 SectorSelectionOutput으로 파싱
     */
    private SectorSelectionOutput parseResponse(String response) {
        try {
            // JSON 추출 (Agent가 마크다운 코드 블록으로 감쌀 수 있음)
            String jsonString = extractJson(response);

            log.debug("Extracted JSON: {}", jsonString);

            // JSON 파싱
            JsonNode root = objectMapper.readTree(jsonString);

            // Leader, Support, Buffer 섹터 추출
            SectorSelectionOutput.SelectedSector leader = parseSector(root.get("leader"));
            SectorSelectionOutput.SelectedSector support = parseSector(root.get("support"));
            SectorSelectionOutput.SelectedSector buffer = parseSector(root.get("buffer"));

            String explanation = root.has("explanation")
                    ? root.get("explanation").asText()
                    : "포트폴리오 전략 설명 없음";

            log.info("Parsed sector selection: Leader={}, Support={}, Buffer={}",
                    leader.sectorId(), support.sectorId(), buffer.sectorId());

            return new SectorSelectionOutput(leader, support, buffer, explanation);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse Agent JSON response", e);
            log.error("Raw response: {}", response);
            throw new SectorSelectionException("Failed to parse Agent response", e);
        }
    }

    /**
     * JSON 노드에서 SelectedSector 추출
     */
    private SectorSelectionOutput.SelectedSector parseSector(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new SectorSelectionException("Invalid sector node in Agent response");
        }

        return new SectorSelectionOutput.SelectedSector(
                node.get("sectorId").asText(),
                node.get("sectorName").asText(),
                node.get("score").asInt(),
                node.get("reason").asText()
        );
    }

    /**
     * 응답에서 JSON 부분만 추출
     * Agent가 ```json ... ``` 형식으로 응답할 수 있으므로 추출 필요
     */
    private String extractJson(String response) {
        if (response == null || response.isBlank()) {
            throw new SectorSelectionException("Empty response from Agent");
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

    public static class SectorSelectionException extends RuntimeException {
        public SectorSelectionException(String message) {
            super(message);
        }

        public SectorSelectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
