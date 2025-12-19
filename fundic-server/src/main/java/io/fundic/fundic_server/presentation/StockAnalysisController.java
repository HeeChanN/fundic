package io.fundic.fundic_server.presentation;

import io.fundic.fundic_server.ai.StockAnalysisResult;
import io.fundic.fundic_server.ai.StockAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 종목 분석 API
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class StockAnalysisController {

    private final StockAnalysisService stockAnalysisService;

    /**
     * 종목 분석 요청
     * POST /api/ai/analyze-stock?stockCode=005930
     *
     * @param stockCode 종목 코드
     * @return AI 분석 결과
     */
    @PostMapping("/analyze-stock")
    public ResponseEntity<?> analyzeStock(@RequestParam String stockCode) {
        try {
            log.info("AI 종목 분석 요청: {}", stockCode);

            StockAnalysisResult result = stockAnalysisService.analyzeStock(stockCode);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", result
            ));

        } catch (Exception e) {
            log.error("AI 종목 분석 실패: {}", stockCode, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "message", "종목 분석 중 오류가 발생했습니다."
            ));
        }
    }

    /**
     * 헬스 체크
     * GET /api/ai/health
     */
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "AI Stock Analysis",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
