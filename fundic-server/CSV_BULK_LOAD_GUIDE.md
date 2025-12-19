# CSV 대량 데이터 적재 가이드

API 호출 없이 FinanceDataReader를 사용하여 빠르게 주가 데이터를 적재하는 방법입니다.

## 예상 소요 시간

- **Python 다운로드**: 2780개 종목 × 30일 = 약 10-20분
- **Java DB 적재**: CSV 레코드 수에 따라 2-5분
- **총 소요 시간**: 약 15-25분 (API 방식의 8시간과 비교하면 20배 이상 빠름!)

---

## 1단계: Python 환경 설정

```bash
# FinanceDataReader 설치
pip install finance-datareader pandas

# 또는
pip3 install finance-datareader pandas
```

---

## 2단계: 주가 데이터 다운로드

```bash
# Python 스크립트 실행
cd /home/ahc70/fundic/fundic-server
python3 download_stock_data.py
```

### 실행 시 동작:
1. KRX에서 전체 상장 종목 조회 (약 2780개)
2. 각 종목별 30일치 일별 시세 다운로드
3. `./stock_data_csv/stock_prices_bulk.csv` 파일로 저장
4. 진행 상황 100개 단위로 출력

### 출력 예시:
```
========================================
주가 데이터 대량 다운로드 시작
========================================

다운로드 기간: 2024-11-19 ~ 2024-12-19
출력 디렉토리: ./stock_data_csv

KRX에서 전체 상장 종목 조회 중...
KRX에서 2780개 종목 로드됨

총 2780개 종목 다운로드 시작...

[100/2780] 진행 중... (성공: 98, 실패: 2, 예상 잔여시간: 15.3분)
[200/2780] 진행 중... (성공: 196, 실패: 4, 예상 잔여시간: 14.1분)
...
```

---

## 3단계: Spring Boot 애플리케이션 시작

```bash
# 현재 실행 중인 프로세스 종료 (필요시)
pkill -f fundic

# 애플리케이션 시작
./gradlew bootRun
```

별도 터미널에서 실행하거나 백그라운드로 실행:
```bash
./gradlew bootRun > /tmp/fundic.log 2>&1 &
```

---

## 4단계: CSV 파일 DB 적재

애플리케이션이 시작되면 REST API로 CSV 로드:

```bash
curl -X POST "http://localhost:8080/api/admin/batch/load-csv?filePath=/home/ahc70/fundic/fundic-server/stock_data_csv/stock_prices_bulk.csv"
```

### 응답 예시:
```json
{
  "success": true,
  "message": "CSV 파일 로드가 시작되었습니다.",
  "filePath": "/home/ahc70/fundic/fundic-server/stock_data_csv/stock_prices_bulk.csv",
  "note": "진행 상황은 로그를 확인하세요."
}
```

---

## 5단계: 진행 상황 모니터링

### 로그 확인:
```bash
tail -f /tmp/fundic.log

# 또는 애플리케이션을 포그라운드로 실행했다면 콘솔에서 직접 확인
```

### 로그 출력 예시:
```
2024-12-19 14:30:15 INFO  CsvDataLoader - CSV 파일 로딩 시작: /home/ahc70/fundic/fundic-server/stock_data_csv/stock_prices_bulk.csv
2024-12-19 14:30:15 INFO  CsvDataLoader - DB에서 2780개 종목 로드됨
2024-12-19 14:30:16 INFO  CsvDataLoader - CSV 헤더: stock_code,trade_date,open_price,high_price,low_price,close_price,volume,change_rate
2024-12-19 14:30:20 INFO  CsvDataLoader - 진행: 5000 / 5000 레코드 저장됨 (성공: 4823, 스킵: 177, 에러: 0)
2024-12-19 14:30:24 INFO  CsvDataLoader - 진행: 10000 / 10000 레코드 저장됨 (성공: 9654, 스킵: 346, 에러: 0)
...
2024-12-19 14:32:45 INFO  CsvDataLoader - CSV 로딩 완료!
2024-12-19 14:32:45 INFO  CsvDataLoader - 총 라인: 58380
2024-12-19 14:32:45 INFO  CsvDataLoader - 성공: 56234
2024-12-19 14:32:45 INFO  CsvDataLoader - 스킵: 2146 (중복 또는 미등록 종목)
2024-12-19 14:32:45 INFO  CsvDataLoader - 에러: 0
2024-12-19 14:32:45 INFO  CsvDataLoader - 기술적 지표 계산 시작...
2024-12-19 14:33:10 INFO  CsvDataLoader - 기술적 지표 계산 진행: 100/2780 종목 완료
...
2024-12-19 14:35:30 INFO  CsvDataLoader - 기술적 지표 계산 완료: 2780 종목
```

---

## 6단계: 데이터 확인

```bash
# 데이터 통계 조회
curl http://localhost:8080/api/admin/batch/stats | python3 -m json.tool
```

### 응답 예시:
```json
{
  "totalRecords": 56234,
  "latestDate": "2024-12-19",
  "oldestDate": "2024-11-19",
  "daysCovered": 30
}
```

---

## 문제 해결

### 1. Python 패키지 설치 오류
```bash
# pip 업그레이드
pip install --upgrade pip

# 또는 pip3 사용
pip3 install finance-datareader pandas
```

### 2. CSV 파일 경로 오류
- 절대 경로 사용 권장
- 파일 존재 확인: `ls -lh ./stock_data_csv/stock_prices_bulk.csv`

### 3. DB 적재 실패
- 애플리케이션이 실행 중인지 확인: `curl http://localhost:8080/api/admin/batch/health`
- 로그에서 에러 확인: `tail -100 /tmp/fundic.log`

### 4. 일부 종목 스킵됨
- 정상 현상: DB에 등록되지 않은 종목 또는 상장폐지 종목
- `download_errors.log` 파일 확인

---

## 장점 비교

| 방법 | 소요 시간 | API 제한 | 장점 |
|------|----------|----------|------|
| **KIS API 방식** | 8시간 | 초당 2회 | 실시간 데이터, 공식 API |
| **CSV 방식** | 15-25분 | 없음 | 빠름, 제한 없음, 간편함 |

---

## 다음 단계

데이터 적재 완료 후:

1. **포트폴리오 추천 API 테스트**
   ```bash
   curl -X POST http://localhost:8080/api/portfolio/recommend \
     -H "Content-Type: application/json" \
     -d '{
       "sectors": [
         {"sectorId": 1, "weight": 50.0},
         {"sectorId": 2, "weight": 50.0}
       ],
       "budget": 10000000
     }'
   ```

2. **정기 배치 설정**
   - 매일 18시: 전일 주가 데이터 자동 수집
   - 분기별: 재무 데이터 자동 수집
   - `application.yml`에서 설정 확인

---

## 참고

- **FinanceDataReader 문서**: https://github.com/FinanceData/FinanceDataReader
- **지원 데이터 소스**: 네이버 금융, KRX, Yahoo Finance
- **CSV 형식**: UTF-8 인코딩, 쉼표 구분
