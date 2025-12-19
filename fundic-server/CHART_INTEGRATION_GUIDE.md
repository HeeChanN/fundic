# TradingView 차트 통합 가이드

포트폴리오 추천 결과에서 종목명을 클릭하면 TradingView 인터랙티브 차트를 볼 수 있습니다.

## 구현 내용

### 1. chart.html (새 파일)
**경로**: `src/main/resources/static/chart.html`

**기능**:
- TradingView 위젯을 임베드한 차트 페이지
- URL 쿼리 파라미터로 종목 코드와 이름 받음
- KRX 한국 주식 실시간 차트 제공

**주요 기능**:
- ✅ 실시간 주가 차트 (일봉 기본)
- ✅ 캔들스틱 차트
- ✅ 기술적 지표 (RSI, 이동평균선)
- ✅ 한국어 인터페이스
- ✅ 인터랙티브 (확대/축소, 시간대 변경)
- ✅ 심볼 변경 가능
- ✅ 경제 캘린더, 인기 종목 표시

**TradingView 심볼 형식**:
```javascript
// 종목 코드를 KRX: 접두사와 함께 사용
const symbol = `KRX:${stockCode.padStart(6, '0')}`;

// 예시:
// 삼성전자 (005930) → KRX:005930
// SK하이닉스 (000660) → KRX:000660
// 카카오 (035720) → KRX:035720
```

### 2. index.html (수정)
**경로**: `src/main/resources/static/index.html`

**변경 사항**:

1. **CSS 추가** (252-262줄):
```css
.stock-table a {
    color: #1e40af;
    text-decoration: none;
    font-weight: 700;
    transition: .2s;
}
.stock-table a:hover {
    color: #2563eb;
    text-decoration: underline;
}
```

2. **종목 테이블 수정** (707-723줄):
```javascript
// 종목명에 차트 링크 추가
const chartUrl = `chart.html?code=${encodeURIComponent(stock.code)}&name=${encodeURIComponent(stock.name)}`;

html += `
  <tr>
    <td>
      <a href="${chartUrl}" target="_blank">
        ${escapeHtml(stock.name)} 📊
      </a>
    </td>
    ...
  </tr>
`;
```

## 사용 방법

### 1. 서버 시작
```bash
cd /home/ahc70/fundic/fundic-server
./gradlew bootRun
```

### 2. 브라우저에서 접속
```
http://localhost:8080/index.html
```

### 3. 포트폴리오 추천 받기
1. 투자 프로필 설정 (기간, 리스크, 목표)
2. Sector 선택 (직접 선택 또는 AI 추천)
3. 포트폴리오 추천 버튼 클릭

### 4. 차트 보기
- 추천 종목 테이블에서 **종목명**을 클릭
- 새 탭에서 TradingView 차트 페이지 열림
- 실시간 인터랙티브 차트 확인

## 화면 구성

### 메인 페이지 (index.html)
```
┌─────────────────────────────────────┐
│  📈 결과                            │
├─────────────────────────────────────┤
│  💼 추천 포트폴리오 (Top 10)        │
│                                     │
│  ┌───────────────────────────────┐ │
│  │ 종목명   │ 코드 │ 분야 │ 비중 │ │
│  ├───────────────────────────────┤ │
│  │ 삼성전자📊│ 005930│ 반도체│10% │ │ ← 클릭 가능
│  │ SK하이닉스📊│ 000660│ 반도체│10%│ │
│  └───────────────────────────────┘ │
└─────────────────────────────────────┘
```

### 차트 페이지 (chart.html)
```
┌─────────────────────────────────────┐
│ ← 돌아가기   삼성전자               │
│              종목코드: 005930        │
├─────────────────────────────────────┤
│  📈 실시간 차트                     │
│  ┌───────────────────────────────┐ │
│  │                               │ │
│  │   [TradingView 차트]          │ │
│  │   - 캔들스틱                  │ │
│  │   - RSI, 이동평균선           │ │
│  │   - 확대/축소                 │ │
│  │   - 기간 변경                 │ │
│  │                               │ │
│  └───────────────────────────────┘ │
└─────────────────────────────────────┘
```

## 기술 스택

| 구성 요소 | 기술 |
|----------|------|
| 백엔드 | Spring Boot (SSR) |
| 프론트엔드 | HTML5, Vanilla JS, CSS3 |
| 차트 라이브러리 | TradingView Widget |
| 데이터 소스 | TradingView (KRX 한국거래소) |

## TradingView 위젯 설정

### 현재 설정:
```javascript
new TradingView.widget({
    "symbol": "KRX:005930",          // 종목 심볼
    "interval": "D",                 // 일봉 (D), 주봉(W), 월봉(M)
    "timezone": "Asia/Seoul",        // 한국 시간
    "theme": "light",                // 테마 (light/dark)
    "style": "1",                    // 캔들스틱
    "locale": "kr",                  // 한국어
    "studies": [                     // 기술적 지표
        "RSI@tv-basicstudies",       // RSI
        "MASimple@tv-basicstudies"   // 이동평균선
    ]
});
```

### 커스터마이징 옵션:

**시간 간격** (`interval`):
- `"1"` - 1분봉
- `"5"` - 5분봉
- `"60"` - 60분봉
- `"D"` - 일봉 (기본값)
- `"W"` - 주봉
- `"M"` - 월봉

**추가 가능한 지표** (`studies`):
```javascript
"MACD@tv-basicstudies"              // MACD
"BB@tv-basicstudies"                // 볼린저 밴드
"Volume@tv-basicstudies"            // 거래량
"Stochastic@tv-basicstudies"        // 스토캐스틱
```

## 장점

### 1. 무료
- TradingView 위젯은 무료로 사용 가능
- API 키 불필요

### 2. 실시간 데이터
- KRX 한국거래소 공식 데이터
- 실시간 시세 반영

### 3. 전문적인 차트
- 증권사 HTS/MTS 수준의 차트
- 다양한 기술적 지표
- 인터랙티브한 UX

### 4. 간단한 통합
- CDN 스크립트 한 줄로 추가
- 종목 코드만 전달하면 자동 렌더링
- 심볼 변환 간단 (KRX: 접두사)

### 5. 모바일 최적화
- 반응형 디자인
- 터치 제스처 지원

## 브라우저 호환성

- ✅ Chrome (권장)
- ✅ Firefox
- ✅ Safari
- ✅ Edge
- ✅ 모바일 브라우저

## 문제 해결

### 1. 차트가 로드되지 않음
**원인**:
- 네트워크 연결 문제
- TradingView CDN 접근 불가
- 잘못된 종목 코드

**해결**:
```javascript
// 콘솔에서 에러 확인
// F12 → Console 탭

// 종목 코드 확인
console.log('Symbol:', `KRX:${stockCode}`);
```

### 2. 종목 데이터가 없음
**원인**:
- 상장폐지 종목
- TradingView 미지원 종목

**해결**:
- 네이버 금융 또는 다른 소스로 대체
- 종목 유효성 사전 검증

### 3. 느린 로딩
**원인**:
- 네트워크 속도
- TradingView 서버 부하

**해결**:
- 로딩 인디케이터 표시 (구현됨)
- 타임아웃 설정

## 다음 단계 개선 사항

### 1. 차트 페이지 고도화
- [ ] 여러 종목 비교 차트
- [ ] 사용자 지표 설정 저장
- [ ] 차트 스크린샷 저장

### 2. DB 데이터 활용
- [ ] 자체 DB 데이터로 간단한 차트 추가 (Chart.js)
- [ ] 기술적 지표 프리셋 제공

### 3. UX 개선
- [ ] 차트 페이지 뒤로가기 동작 개선
- [ ] 모달 형태로 차트 표시 옵션
- [ ] 다크 모드 지원

## 참고 자료

- [TradingView Widget 공식 문서](https://www.tradingview.com/widget/)
- [TradingView 심볼 검색](https://www.tradingview.com/symbols/)
- [TradingView 위젯 커스터마이징](https://www.tradingview.com/widget-wizard/)

---

## 시연 시나리오

**시연 날짜**: 내일

**시연 흐름**:
1. ✅ "투자자 프로필 설정하고"
2. ✅ "AI로 섹터 추천 받아서"
3. ✅ "포트폴리오 추천 받고"
4. ✅ **"종목 클릭하면 → 실시간 차트 바로 확인!"** ← 새로운 기능!

**강조 포인트**:
- "실제 HTS처럼 전문적인 차트"
- "TradingView 기반 실시간 데이터"
- "한 번의 클릭으로 종목 상세 분석"
