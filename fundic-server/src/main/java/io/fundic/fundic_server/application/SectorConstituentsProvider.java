package io.fundic.fundic_server.application;

import java.util.List;

public interface SectorConstituentsProvider {
    List<String> getStockCodesBySector(String sectorId);
}
