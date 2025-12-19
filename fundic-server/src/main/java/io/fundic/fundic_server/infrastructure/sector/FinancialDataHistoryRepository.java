package io.fundic.fundic_server.infrastructure.sector;

import io.fundic.fundic_server.domain.FinancialDataHistory;
import io.fundic.fundic_server.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FinancialDataHistoryRepository extends JpaRepository<FinancialDataHistory, Long> {

    /**
     * Find latest financial data for a stock
     */
    @Query(value = "SELECT * FROM financial_data_history WHERE stock_id = :stockId " +
           "ORDER BY fiscal_year DESC, fiscal_quarter DESC LIMIT 1", nativeQuery = true)
    Optional<FinancialDataHistory> findLatestByStockId(@Param("stockId") Long stockId);

    /**
     * Batch query for multiple stocks (latest data)
     */
    @Query("SELECT f FROM FinancialDataHistory f WHERE f.stock.code IN :stockCodes " +
           "AND (f.fiscalYear, f.fiscalQuarter) IN " +
           "(SELECT f2.fiscalYear, f2.fiscalQuarter FROM FinancialDataHistory f2 " +
           "WHERE f2.stock.code = f.stock.code " +
           "ORDER BY f2.fiscalYear DESC, f2.fiscalQuarter DESC LIMIT 1)")
    List<FinancialDataHistory> findLatestByStockCodes(@Param("stockCodes") List<String> stockCodes);

    /**
     * Check if data exists for specific quarter
     */
    boolean existsByStockAndFiscalYearAndFiscalQuarter(
            Stock stock,
            Integer fiscalYear,
            Integer fiscalQuarter
    );

    /**
     * Find latest 4 quarters of financial data for AI analysis
     */
    @Query(value = "SELECT * FROM financial_data_history WHERE stock_id = :stockId " +
           "ORDER BY fiscal_year DESC, fiscal_quarter DESC LIMIT 4", nativeQuery = true)
    List<FinancialDataHistory> findTop4ByStockOrderByFiscalYearDescFiscalQuarterDesc(@Param("stockId") Long stockId);
}
