package io.fundic.fundic_server.infrastructure.sector;

import io.fundic.fundic_server.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByCode(String code);

    @Query("SELECT s FROM Stock s WHERE s.sector.id = :sectorId")
    List<Stock> findBySectorId(@Param("sectorId") Long sectorId);
}
