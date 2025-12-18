package io.fundic.fundic_server.application;

import io.fundic.fundic_server.domain.Stock;
import io.fundic.fundic_server.infrastructure.sector.StockRepository;
import io.fundic.fundic_server.presentation.dto.StockDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;

    public List<StockDto> getStocksBySectorId(Long sectorId) {
        List<Stock> stocks = stockRepository.findBySectorId(sectorId);
        return stocks.stream()
                .map(st -> new StockDto(
                        st.getCode(),
                        st.getName(),
                        st.getMarket(),
                        st.getSector().getId(),
                        st.getSector().getName()
                )).toList();
    }
}
