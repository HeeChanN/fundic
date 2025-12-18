package io.fundic.fundic_server.presentation.dto;

import io.fundic.fundic_server.domain.Market;

public record StockDto(
        String code,
        String name,
        Market market,
        Long sectorId,
        String sectorName
) {}