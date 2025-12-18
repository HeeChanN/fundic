package io.fundic.fundic_server.presentation;

import io.fundic.fundic_server.application.StockService;
import io.fundic.fundic_server.presentation.dto.StockDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping("/sectors/{sectorId}/stocks")
    public List<StockDto> stocksBySector(@PathVariable("sectorId") Long sectorId) {
        return stockService.getStocksBySectorId(sectorId);
    }
}
