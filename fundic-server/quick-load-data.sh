#!/bin/bash

# 주가 데이터 빠른 적재 스크립트
# Usage: ./quick-load-data.sh [days]

DAYS=${1:-30}
BASE_DIR="/home/ahc70/fundic/fundic-server"
CSV_DIR="$BASE_DIR/stock_data_csv"
CSV_FILE="$CSV_DIR/stock_prices_bulk.csv"

echo "========================================="
echo "  주가 데이터 빠른 적재 (CSV 방식)"
echo "========================================="
echo ""
echo "기간: 최근 ${DAYS}일"
echo ""

# 1. FinanceDataReader 설치 확인
echo "[1/4] Python 패키지 확인 중..."
python3 -c "import FinanceDataReader" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "FinanceDataReader가 설치되지 않았습니다."
    echo "설치 중: pip3 install finance-datareader pandas"
    pip3 install finance-datareader pandas
    if [ $? -ne 0 ]; then
        echo "❌ 패키지 설치 실패!"
        exit 1
    fi
fi
echo "✅ Python 패키지 확인 완료"
echo ""

# 2. 데이터 다운로드
echo "[2/4] 주가 데이터 다운로드 중..."
echo "예상 소요 시간: 10-20분"
echo ""

cd "$BASE_DIR"

# Python 스크립트에 DAYS 파라미터 전달하도록 수정
python3 - <<EOF
import FinanceDataReader as fdr
import pandas as pd
from datetime import datetime, timedelta
import os
from pathlib import Path

DAYS_BACK = $DAYS
OUTPUT_DIR = "$CSV_DIR"
ERROR_LOG = "$BASE_DIR/download_errors.log"

Path(OUTPUT_DIR).mkdir(exist_ok=True)

end_date = datetime.now()
start_date = end_date - timedelta(days=DAYS_BACK)

print(f"다운로드 기간: {start_date.date()} ~ {end_date.date()}")

# KRX에서 전체 종목 가져오기
print("KRX에서 전체 상장 종목 조회 중...")
krx_stocks = fdr.StockListing('KRX')
stock_codes = krx_stocks['Code'].tolist()
print(f"총 {len(stock_codes)}개 종목 다운로드 시작...")
print()

all_data = []
success_count = 0
fail_count = 0
errors = []

import time
start_time = time.time()

for idx, code in enumerate(stock_codes, 1):
    try:
        df = fdr.DataReader(code, start=start_date.strftime('%Y-%m-%d'), end=end_date.strftime('%Y-%m-%d'))

        if df.empty:
            errors.append(f"{code}: 데이터 없음")
            fail_count += 1
            continue

        df['stock_code'] = code
        df['trade_date'] = df.index

        df = df.rename(columns={
            'Open': 'open_price',
            'High': 'high_price',
            'Low': 'low_price',
            'Close': 'close_price',
            'Volume': 'volume',
            'Change': 'change_rate'
        })

        df = df[['stock_code', 'trade_date', 'open_price', 'high_price',
                'low_price', 'close_price', 'volume', 'change_rate']]

        all_data.append(df)
        success_count += 1

        if idx % 100 == 0:
            elapsed = time.time() - start_time
            avg_time = elapsed / idx
            remaining = (len(stock_codes) - idx) * avg_time
            print(f"[{idx}/{len(stock_codes)}] 진행 중... (성공: {success_count}, 실패: {fail_count}, 예상 잔여시간: {remaining/60:.1f}분)")

        time.sleep(0.05)

    except Exception as e:
        errors.append(f"{code}: {str(e)}")
        fail_count += 1

if all_data:
    print()
    print("데이터 병합 및 저장 중...")
    combined_df = pd.concat(all_data, ignore_index=True)
    output_file = "$CSV_FILE"
    combined_df.to_csv(output_file, index=False, encoding='utf-8-sig')
    print(f"✅ 전체 데이터 저장 완료: {output_file}")
    print(f"   총 레코드 수: {len(combined_df):,}개")

if errors:
    with open(ERROR_LOG, 'w', encoding='utf-8') as f:
        f.write('\n'.join(errors))

elapsed_total = time.time() - start_time
print()
print("다운로드 완료!")
print(f"성공: {success_count}개, 실패: {fail_count}개")
print(f"소요 시간: {elapsed_total/60:.1f}분")
EOF

if [ $? -ne 0 ]; then
    echo "❌ 데이터 다운로드 실패!"
    exit 1
fi

echo ""
echo "✅ 데이터 다운로드 완료"
echo ""

# 3. Spring Boot 실행 확인
echo "[3/4] Spring Boot 애플리케이션 확인 중..."
curl -s http://localhost:8080/api/admin/batch/health > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "Spring Boot 애플리케이션이 실행되지 않았습니다."
    echo "애플리케이션을 시작하려면 다른 터미널에서 다음 명령 실행:"
    echo "  cd $BASE_DIR && ./gradlew bootRun"
    echo ""
    echo "애플리케이션이 시작되면 다음 명령으로 CSV 로드:"
    echo "  curl -X POST \"http://localhost:8080/api/admin/batch/load-csv?filePath=$CSV_FILE\""
    exit 1
fi
echo "✅ Spring Boot 실행 중"
echo ""

# 4. CSV 파일 DB 적재
echo "[4/4] CSV 파일 DB 적재 중..."
echo "API 호출: POST /api/admin/batch/load-csv"
echo ""

RESPONSE=$(curl -s -X POST "http://localhost:8080/api/admin/batch/load-csv?filePath=$CSV_FILE")
echo "$RESPONSE" | python3 -m json.tool

SUCCESS=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('success', False))")

if [ "$SUCCESS" = "True" ]; then
    echo ""
    echo "✅ CSV 로드 시작됨!"
    echo ""
    echo "진행 상황 모니터링:"
    echo "  tail -f /tmp/fundic.log"
    echo ""
    echo "데이터 통계 확인:"
    echo "  curl http://localhost:8080/api/admin/batch/stats | python3 -m json.tool"
else
    echo ""
    echo "❌ CSV 로드 실패!"
fi

echo ""
echo "========================================="
echo "  작업 완료!"
echo "========================================="
