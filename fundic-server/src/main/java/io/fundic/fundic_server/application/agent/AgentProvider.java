package io.fundic.fundic_server.application.agent;

/**
 * AI Agent의 기본 인터페이스
 * @param <INPUT> Agent에 전달할 입력 데이터 타입
 * @param <OUTPUT> Agent가 반환할 출력 데이터 타입
 */
public interface AgentProvider<INPUT, OUTPUT> {
    /**
     * Agent를 실행하여 결과를 반환
     * @param input Agent 입력 데이터
     * @return Agent 실행 결과
     */
    OUTPUT execute(INPUT input);

    /**
     * Agent 이름 반환 (로깅 및 모니터링용)
     */
    String getAgentName();

    /**
     * Agent 설정 반환
     */
    AgentConfig getConfig();
}
