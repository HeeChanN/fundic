#!/usr/bin/env python3
"""
주가 데이터 대량 다운로드 스크립트
FinanceDataReader를 사용하여 2780개 종목의 30일치 데이터를 CSV로 저장
"""

import FinanceDataReader as fdr
import pandas as pd
from datetime import datetime, timedelta
import os
import time
from pathlib import Path

# 설정
DAYS_BACK = 30
OUTPUT_DIR = "./stock_data_csv"
ERROR_LOG = "./download_errors.log"

def main():
    print("=" * 60)
    print("주가 데이터 대량 다운로드 시작")
    print("=" * 60)
    print()

    # 출력 디렉토리 생성
    Path(OUTPUT_DIR).mkdir(exist_ok=True)

    # 날짜 범위 설정
    end_date = datetime.now()
    start_date = end_date - timedelta(days=DAYS_BACK)

    print(f"다운로드 기간: {start_date.date()} ~ {end_date.date()}")
    print(f"출력 디렉토리: {OUTPUT_DIR}")
    print()

    # 종목 코드 파일 읽기 (또는 직접 리스트로 제공)
    # Option 1: 파일에서 읽기
    stock_codes_file = "./stock_codes.txt"
    if os.path.exists(stock_codes_file):
        with open(stock_codes_file, 'r') as f:
            stock_codes = [line.strip() for line in f if line.strip()]
        print(f"종목 코드 파일에서 {len(stock_codes)}개 종목 로드됨")
    else:
        # Option 2: KRX에서 전체 종목 가져오기
        print("KRX에서 전체 상장 종목 조회 중...")
        krx_stocks = fdr.StockListing('KRX')
        stock_codes = krx_stocks['Code'].tolist()
        print(f"KRX에서 {len(stock_codes)}개 종목 로드됨")

        # 종목 코드 파일로 저장 (다음 실행 시 재사용)
        with open(stock_codes_file, 'w') as f:
            f.write('\n'.join(stock_codes))
        print(f"종목 코드를 {stock_codes_file}에 저장했습니다.")

    print()
    print(f"총 {len(stock_codes)}개 종목 다운로드 시작...")
    print()

    # 통계
    success_count = 0
    fail_count = 0
    errors = []

    # 전체 데이터를 하나의 DataFrame으로 모으기
    all_data = []

    start_time = time.time()

    for idx, code in enumerate(stock_codes, 1):
        try:
            # 데이터 다운로드
            df = fdr.DataReader(
                code,
                start=start_date.strftime('%Y-%m-%d'),
                end=end_date.strftime('%Y-%m-%d')
            )

            if df.empty:
                errors.append(f"{code}: 데이터 없음")
                fail_count += 1
                continue

            # 종목 코드 컬럼 추가
            df['stock_code'] = code
            df['trade_date'] = df.index

            # 컬럼명 매핑
            df = df.rename(columns={
                'Open': 'open_price',
                'High': 'high_price',
                'Low': 'low_price',
                'Close': 'close_price',
                'Volume': 'volume',
                'Change': 'change_rate'
            })

            # 필요한 컬럼만 선택
            df = df[['stock_code', 'trade_date', 'open_price', 'high_price',
                    'low_price', 'close_price', 'volume', 'change_rate']]

            all_data.append(df)
            success_count += 1

            # 진행 상황 출력
            if idx % 100 == 0:
                elapsed = time.time() - start_time
                avg_time = elapsed / idx
                remaining = (len(stock_codes) - idx) * avg_time
                print(f"[{idx}/{len(stock_codes)}] 진행 중... "
                      f"(성공: {success_count}, 실패: {fail_count}, "
                      f"예상 잔여시간: {remaining/60:.1f}분)")

            # API 부하 방지를 위한 짧은 대기
            time.sleep(0.05)

        except Exception as e:
            error_msg = f"{code}: {str(e)}"
            errors.append(error_msg)
            fail_count += 1

            if idx % 100 == 0:
                print(f"[{idx}/{len(stock_codes)}] 진행 중... "
                      f"(성공: {success_count}, 실패: {fail_count})")

    # 전체 데이터 병합 및 저장
    if all_data:
        print()
        print("데이터 병합 및 저장 중...")
        combined_df = pd.concat(all_data, ignore_index=True)

        # CSV 파일로 저장
        output_file = f"{OUTPUT_DIR}/stock_prices_bulk.csv"
        combined_df.to_csv(output_file, index=False, encoding='utf-8-sig')
        print(f"✅ 전체 데이터 저장 완료: {output_file}")
        print(f"   총 레코드 수: {len(combined_df):,}개")

    # 에러 로그 저장
    if errors:
        with open(ERROR_LOG, 'w', encoding='utf-8') as f:
            f.write('\n'.join(errors))
        print(f"\n⚠️  에러 로그 저장: {ERROR_LOG}")

    # 최종 통계
    elapsed_total = time.time() - start_time
    print()
    print("=" * 60)
    print("다운로드 완료!")
    print("=" * 60)
    print(f"총 종목 수: {len(stock_codes)}개")
    print(f"성공: {success_count}개")
    print(f"실패: {fail_count}개")
    print(f"소요 시간: {elapsed_total/60:.1f}분")
    print(f"평균 속도: {len(stock_codes)/elapsed_total:.1f}종목/초")
    print()

    if all_data:
        print(f"다음 단계: 아래 Java 프로그램을 실행하여 DB에 적재하세요")
        print(f"  ./gradlew run --args='load-csv {output_file}'")

if __name__ == "__main__":
    # 필요한 패키지 확인
    try:
        import FinanceDataReader
    except ImportError:
        print("FinanceDataReader가 설치되지 않았습니다.")
        print("설치 명령: pip install finance-datareader")
        exit(1)

    main()
