#!/bin/bash

# 백필 모니터링 스크립트
# Usage: ./monitor-backfill.sh

BASE_URL="http://localhost:8080/api/admin/batch"

echo "========================================="
echo "    백필 작업 모니터링 시작"
echo "========================================="
echo ""

# 1. 백필 시작
echo "백필 작업을 시작합니다..."
START_RESPONSE=$(curl -s -X POST "${BASE_URL}/backfill?days=90")
echo "$START_RESPONSE" | jq .

SUCCESS=$(echo "$START_RESPONSE" | jq -r '.success')
if [ "$SUCCESS" != "true" ]; then
    echo "백필 시작 실패!"
    exit 1
fi

echo ""
echo "진행 상황을 모니터링합니다... (Ctrl+C로 중단)"
echo ""

# 2. 진행 상황 모니터링
while true; do
    STATUS=$(curl -s "${BASE_URL}/backfill/status")
    IS_RUNNING=$(echo "$STATUS" | jq -r '.isRunning')
    PROGRESS=$(echo "$STATUS" | jq -r '.progress')
    STATUS_MSG=$(echo "$STATUS" | jq -r '.status')

    # 진행률 바 생성
    FILLED=$((PROGRESS / 2))  # 50칸 기준
    BAR=$(printf '%*s' "$FILLED" | tr ' ' '█')
    EMPTY=$(printf '%*s' $((50 - FILLED)) | tr ' ' '░')

    # 화면 지우고 출력
    clear
    echo "========================================="
    echo "    백필 작업 진행 상황"
    echo "========================================="
    echo ""
    echo "상태: $STATUS_MSG"
    echo "진행률: [$BAR$EMPTY] $PROGRESS%"
    echo ""

    # 데이터 통계 조회
    STATS=$(curl -s "${BASE_URL}/stats")
    TOTAL=$(echo "$STATS" | jq -r '.totalRecords')
    LATEST=$(echo "$STATS" | jq -r '.latestDate')
    OLDEST=$(echo "$STATS" | jq -r '.oldestDate')

    echo "저장된 레코드: $TOTAL"
    echo "최신 날짜: $LATEST"
    echo "가장 오래된 날짜: $OLDEST"
    echo ""
    echo "========================================="

    if [ "$IS_RUNNING" = "false" ]; then
        echo ""
        echo "✅ 백필 작업 완료!"
        break
    fi

    sleep 5
done

# 3. 최종 통계 출력
echo ""
echo "최종 통계:"
curl -s "${BASE_URL}/stats" | jq .
