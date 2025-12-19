package io.fundic.fundic_server.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
@Table(
        name = "financial_data_history",
        indexes = {
                @Index(name = "idx_stock_quarter", columnList = "stock_id, fiscal_year, fiscal_quarter"),
                @Index(name = "idx_fiscal_period", columnList = "fiscal_year, fiscal_quarter")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_fiscal_period",
                columnNames = {"stock_id", "fiscal_year", "fiscal_quarter"}
        )
)
public class FinancialDataHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(name = "fiscal_quarter", nullable = false)
    private Integer fiscalQuarter;

    // Financial metrics
    @Column
    private Double per;

    @Column
    private Double pbr;

    @Column
    private Double roe;

    @Column(name = "operating_margin")
    private Double operatingMargin;

    @Column(name = "debt_ratio")
    private Double debtRatio;

    @Column(name = "market_cap")
    private Long marketCap;

    @Column
    private Double eps;

    @Column(name = "data_date")
    private LocalDate dataDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static FinancialDataHistory of(
            Stock stock,
            Integer fiscalYear,
            Integer fiscalQuarter,
            Double per,
            Double pbr,
            Double roe,
            Double operatingMargin,
            Double debtRatio,
            Long marketCap,
            Double eps,
            LocalDate dataDate
    ) {
        FinancialDataHistory history = new FinancialDataHistory();
        history.stock = stock;
        history.fiscalYear = fiscalYear;
        history.fiscalQuarter = fiscalQuarter;
        history.per = per;
        history.pbr = pbr;
        history.roe = roe;
        history.operatingMargin = operatingMargin;
        history.debtRatio = debtRatio;
        history.marketCap = marketCap;
        history.eps = eps;
        history.dataDate = dataDate;
        return history;
    }
}
