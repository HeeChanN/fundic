package io.fundic.fundic_server.global.bootstrap;

import io.fundic.fundic_server.domain.Market;

import java.math.BigDecimal;

public record CsvStockRow(
        String code,
        String name,
        Market market,
        String sectorName,
        Long closePrice,
        Long changeAmount,
        BigDecimal changeRate,
        Long marketCap
) {}
