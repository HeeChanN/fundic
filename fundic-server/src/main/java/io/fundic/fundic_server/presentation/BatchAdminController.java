package io.fundic.fundic_server.presentation;

import io.fundic.fundic_server.batch.CsvDataLoader;
import io.fundic.fundic_server.batch.StockDataBatchLoader;
import io.fundic.fundic_server.infrastructure.sector.StockPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 배치 작업 관리 API
 * 백필, 수동 데이터 로드 등의 관리 기능 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/batch")
@RequiredArgsConstructor
public class BatchAdminController {

    private final StockDataBatchLoader batchLoader;
    private final CsvDataLoader csvDataLoader;
    private final StockPriceHistoryRepository priceHistoryRepository;

    // 백필 진행 상태 추적
    private static final AtomicBoolean isBackfillRunning = new AtomicBoolean(false);
    private static final AtomicInteger backfillProgress = new AtomicInteger(0);
    private static volatile String backfillStatus = "idle";

    /**
     * 백필 작업 시작
     * POST /api/admin/batch/backfill?days=90&maxDaysPerRun=10
     *
     * @param days 백필할 총 일수 (기본값: 90일)
     * @param maxDaysPerRun 1회 실행당 최대 처리 일수 (기본값: -1, 무제한)
     * @return 백필 시작 메시지
     */
    @PostMapping("/backfill")
    public ResponseEntity<?> startBackfill(
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(defaultValue = "-1") int maxDaysPerRun) {

        // 중복 실행 방지
        if (isBackfillRunning.get()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "백필 작업이 이미 실행 중입니다.",
                    "progress", backfillProgress.get() + "%"
            ));
        }

        // 유효성 검증
        if (days < 1 || days > 365) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "백필 일수는 1~365일 사이여야 합니다.",
                    "requested", days
            ));
        }

        if (maxDaysPerRun > 0 && maxDaysPerRun > days) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "maxDaysPerRun은 days보다 작거나 같아야 합니다.",
                    "days", days,
                    "maxDaysPerRun", maxDaysPerRun
            ));
        }

        // 비동기로 백필 실행
        new Thread(() -> {
            try {
                isBackfillRunning.set(true);
                backfillProgress.set(0);
                backfillStatus = "running";

                log.info("Starting backfill for {} days (maxDaysPerRun: {}) via API request",
                    days, maxDaysPerRun);
                batchLoader.backfillHistoricalData(days, maxDaysPerRun);

                backfillStatus = "completed";
                backfillProgress.set(100);
                log.info("Backfill completed successfully");

            } catch (Exception e) {
                backfillStatus = "failed: " + e.getMessage();
                log.error("Backfill failed", e);
            } finally {
                isBackfillRunning.set(false);
            }
        }).start();

        String message = maxDaysPerRun > 0 ?
            String.format("백필 작업이 시작되었습니다. (대상: %d일, 1회당 최대: %d일)", days, maxDaysPerRun) :
            String.format("백필 작업이 시작되었습니다. (대상: %d일)", days);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", message,
                "days", days,
                "maxDaysPerRun", maxDaysPerRun,
                "estimatedTime", estimateBackfillTime(days, 2780)
        ));
    }

    /**
     * 백필 예상 시간 계산
     */
    private String estimateBackfillTime(int days, int stockCount) {
        // 초당 5회 = 200ms/request
        // 주말 제외: days * 0.71 (대략)
        int tradingDays = (int) (days * 0.71);
        long totalRequests = (long) tradingDays * stockCount;
        long totalSeconds = totalRequests / 5; // 초당 5회
        long totalMinutes = totalSeconds / 60;
        long totalHours = totalMinutes / 60;

        if (totalHours > 0) {
            return String.format("약 %d시간 %d분 예상", totalHours, totalMinutes % 60);
        } else {
            return String.format("약 %d분 예상", totalMinutes);
        }
    }

    /**
     * 백필 작업 상태 확인
     * GET /api/admin/batch/backfill/status
     *
     * @return 백필 진행 상태
     */
    @GetMapping("/backfill/status")
    public ResponseEntity<?> getBackfillStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isRunning", isBackfillRunning.get());
        status.put("progress", backfillProgress.get());
        status.put("status", backfillStatus);

        return ResponseEntity.ok(status);
    }

    /**
     * 저장된 데이터 통계 조회
     * GET /api/admin/batch/stats
     *
     * @return 데이터 통계 정보
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getDataStats() {
        // 전체 레코드 수
        long totalRecords = priceHistoryRepository.count();

        // 가장 최근 데이터 날짜
        LocalDate latestDate = priceHistoryRepository.findAll().stream()
                .map(h -> h.getTradeDate())
                .max(LocalDate::compareTo)
                .orElse(null);

        // 가장 오래된 데이터 날짜
        LocalDate oldestDate = priceHistoryRepository.findAll().stream()
                .map(h -> h.getTradeDate())
                .min(LocalDate::compareTo)
                .orElse(null);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRecords", totalRecords);
        stats.put("latestDate", latestDate);
        stats.put("oldestDate", oldestDate);

        if (latestDate != null && oldestDate != null) {
            long daysCovered = java.time.temporal.ChronoUnit.DAYS.between(oldestDate, latestDate) + 1;
            stats.put("daysCovered", daysCovered);
        }

        return ResponseEntity.ok(stats);
    }

    /**
     * 일별 스케줄러 수동 실행
     * POST /api/admin/batch/daily
     *
     * @return 실행 결과
     */
    @PostMapping("/daily")
    public ResponseEntity<?> runDailyLoad() {
        try {
            log.info("Manual daily load triggered via API");
            batchLoader.loadDailyStockPrices();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "일별 데이터 로드가 완료되었습니다."
            ));
        } catch (Exception e) {
            log.error("Manual daily load failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "일별 데이터 로드 실패: " + e.getMessage()
            ));
        }
    }

    /**
     * 분기별 스케줄러 수동 실행
     * POST /api/admin/batch/quarterly
     *
     * @return 실행 결과
     */
    @PostMapping("/quarterly")
    public ResponseEntity<?> runQuarterlyLoad() {
        try {
            log.info("Manual quarterly load triggered via API");
            batchLoader.loadQuarterlyFinancialData();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "분기별 재무 데이터 로드가 완료되었습니다."
            ));
        } catch (Exception e) {
            log.error("Manual quarterly load failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "분기별 데이터 로드 실패: " + e.getMessage()
            ));
        }
    }

    /**
     * CSV 파일에서 데이터 로드
     * POST /api/admin/batch/load-csv?filePath=/Users/ahc70/Desktop/personal_project/fundic/fundic-server/src/main/resources/data/ohlcv_merged.csv
     *
     * @param filePath CSV 파일 경로
     * @return 로드 결과
     */
    @PostMapping("/load-csv")
    public ResponseEntity<?> loadFromCsv(@RequestParam String filePath) {
        try {
            log.info("CSV 파일 로드 요청: {}", filePath);

            // 비동기로 실행
            new Thread(() -> {
                try {
                    csvDataLoader.loadFromCsv(filePath);
                    log.info("CSV 로드 완료: {}", filePath);
                } catch (Exception e) {
                    log.error("CSV 로드 실패: {}", filePath, e);
                }
            }).start();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "CSV 파일 로드가 시작되었습니다.",
                    "filePath", filePath,
                    "note", "진행 상황은 로그를 확인하세요."
            ));
        } catch (Exception e) {
            log.error("CSV 로드 시작 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "CSV 로드 실패: " + e.getMessage()
            ));
        }
    }

    /**
     * 헬스 체크
     * GET /api/admin/batch/health
     *
     * @return 시스템 상태
     */
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("backfillRunning", isBackfillRunning.get());
        health.put("timestamp", LocalDate.now());

        return ResponseEntity.ok(health);
    }
}
