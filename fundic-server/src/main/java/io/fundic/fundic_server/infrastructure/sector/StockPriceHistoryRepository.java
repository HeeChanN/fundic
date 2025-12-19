package io.fundic.fundic_server.infrastructure.sector;

import io.fundic.fundic_server.domain.Stock;
import io.fundic.fundic_server.domain.StockPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockPriceHistoryRepository extends JpaRepository<StockPriceHistory, Long> {

    /**
     * Find latest price for a stock
     */
    Optional<StockPriceHistory> findFirstByStockOrderByTradeDateDesc(Stock stock);

    /**
     * Find price history within date range
     */
    @Query("SELECT h FROM StockPriceHistory h WHERE h.stock.id = :stockId " +
           "AND h.tradeDate BETWEEN :startDate AND :endDate ORDER BY h.tradeDate DESC")
    List<StockPriceHistory> findByStockIdAndDateRange(
            @Param("stockId") Long stockId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Batch query for multiple stocks (latest data)
     */
    @Query("SELECT h FROM StockPriceHistory h WHERE h.stock.code IN :stockCodes " +
           "AND h.tradeDate = (SELECT MAX(h2.tradeDate) FROM StockPriceHistory h2 WHERE h2.stock.code = h.stock.code)")
    List<StockPriceHistory> findLatestByStockCodes(@Param("stockCodes") List<String> stockCodes);

    /**
     * Check if data exists for a specific date
     */
    boolean existsByStockAndTradeDate(Stock stock, LocalDate tradeDate);

    /**
     * Get last N days of data
     */
    @Query(value = "SELECT * FROM stock_price_history WHERE stock_id = :stockId " +
           "ORDER BY trade_date DESC LIMIT :limit", nativeQuery = true)
    List<StockPriceHistory> findTopNByStockIdOrderByTradeDateDesc(
            @Param("stockId") Long stockId,
            @Param("limit") int limit
    );

    /**
     * Find price history within date range (ordered by date ascending for analysis)
     */
    List<StockPriceHistory> findByStockAndTradeDateBetweenOrderByTradeDateAsc(
            Stock stock,
            LocalDate startDate,
            LocalDate endDate
    );
}
