# Google ADK (Agent Development Kit) 설정 가이드

## Google ADK란?

Google ADK는 LLM 기반 Agent를 쉽게 개발할 수 있는 프레임워크입니다.
- **Java, Python, TypeScript, Go** 지원
- **무료 Gemini API** 사용 가능
- Agent 패턴, Tool 사용, Memory 관리 등 기본 제공

공식 문서: https://google.github.io/adk-docs/

## API Key 발급받기

1. **Google AI Studio 접속**
   - https://aistudio.google.com/app/apikey 방문

2. **API Key 생성**
   - "Create API Key" 버튼 클릭
   - 프로젝트 선택 또는 새 프로젝트 생성
   - API Key 복사

## 환경 변수 설정

### Option 1: 환경 변수로 설정 (권장)
```bash
export GOOGLE_API_KEY="your-api-key-here"
```

### Option 2: .env 파일 생성
프로젝트 루트에 `.env` 파일 생성:

```
GOOGLE_API_KEY=your-api-key-here
```

**⚠️ 주의**: `.env` 파일을 `.gitignore`에 추가하여 Git에 커밋되지 않도록 해야 합니다!

## 프로젝트 의존성

`build.gradle`에 이미 추가되어 있습니다:

```gradle
implementation 'com.google.adk:google-adk:0.3.0'
```

## 무료 티어 제한

Google Gemini API 무료 티어:
- **분당 요청**: 15 requests/minute
- **일일 요청**: 1,500 requests/day
- **모델**: gemini-2.0-flash-exp (무료, 최신)

개발 및 테스트에 충분한 용량입니다!

## Agent 구조

현재 프로젝트에서 구현된 Agent:

### 1. StockSelectionAgent
- **역할**: 섹터 내 종목 분석 및 Top 4 선정
- **입력**: 섹터 ID, 종목 리스트, 사용자 프로필
- **출력**: 선택된 종목 + 추천 이유 + 리스크 분석

### 2. PortfolioAgent (예정)
- **역할**: 포트폴리오 구성 및 비중 조정
- **입력**: 3개 섹터 + 종목 추천 결과
- **출력**: 섹터/종목 비중 + 리밸런싱 룰 + 설명

## 실행 방법

### 1. 환경 변수 설정 확인
```bash
echo $GOOGLE_API_KEY
```

### 2. 애플리케이션 실행
```bash
./gradlew bootRun
```

### 3. 로그 확인
다음 메시지가 보이면 성공:
```
StockSelectionAgent initialized
```

## 테스트

Agent를 테스트하려면:

```bash
curl -X POST http://localhost:8080/api/v1/recommend/sectors \
  -H "Content-Type: application/json" \
  -d '{
    "horizon": "M1_6",
    "risk": "MEDIUM",
    "goal": "GROWTH",
    "themeKeywords": ["반도체"]
  }'
```

## 트러블슈팅

### 1. API Key 관련 에러
```
Error: GOOGLE_API_KEY not set
```
→ 환경 변수가 설정되지 않았습니다. `export GOOGLE_API_KEY="..."` 실행

### 2. Rate Limit 에러
```
429 Too Many Requests
```
→ 분당 15회 제한에 도달했습니다. 1분 후 재시도하세요.

### 3. Agent 초기화 실패
→ `build.gradle`에서 `google-adk:0.3.0` 의존성이 정상적으로 추가되었는지 확인:
```bash
./gradlew dependencies | grep google-adk
```

## Google ADK 주요 개념

### LlmAgent
```java
LlmAgent agent = LlmAgent.builder()
    .name("my-agent")
    .model("gemini-2.0-flash-exp")
    .instruction("시스템 프롬프트")
    .build();
```

### FunctionTool (예정)
Agent가 외부 데이터를 가져올 수 있도록 Tool 제공:
```java
FunctionTool.create(ToolClass.class, "methodName")
```

## 다음 단계

1. ✅ StockSelectionAgent 기본 구현 완료
2. ⏳ 실제 데이터 통합 (KIS API, 뉴스, 재무)
3. ⏳ PortfolioAgent 구현
4. ⏳ FunctionTool 추가 (종목 데이터 조회)

더 자세한 내용은 공식 문서를 참고하세요: https://google.github.io/adk-docs/get-started/java/
