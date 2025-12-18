package io.fundic.fundic_server.application.agent.stock;

import java.util.List;

/**
 * 종목 선택 Agent 출력 데이터
 */
public record StockSelectionOutput(
        List<SelectedStock> selectedStocks
) {
    public record SelectedStock(
            String stockCode,
            String stockName,
            Integer score,
            String reason,
            String risks
    ) {}
}
