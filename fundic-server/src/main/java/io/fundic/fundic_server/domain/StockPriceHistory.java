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
        name = "stock_price_history",
        indexes = {
                @Index(name = "idx_stock_date", columnList = "stock_id, trade_date"),
                @Index(name = "idx_trade_date", columnList = "trade_date"),
                @Index(name = "idx_stock_id", columnList = "stock_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_date",
                columnNames = {"stock_id", "trade_date"}
        )
)
public class StockPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    // OHLCV data
    @Column(nullable = false)
    private Integer openPrice;

    @Column(nullable = false)
    private Integer highPrice;

    @Column(nullable = false)
    private Integer lowPrice;

    @Column(nullable = false)
    private Integer closePrice;

    @Column(nullable = false)
    private Long volume;

    // Derived metrics
    @Column(nullable = false)
    private Double changeRate;

    @Column
    private Integer previousClose;

    // Technical indicators (calculated during batch load)
    @Column
    private Double rsi;

    @Column
    private Double macd;

    @Column
    private Double ma20;

    @Column
    private Double ma60;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static StockPriceHistory of(
            Stock stock,
            LocalDate tradeDate,
            Integer openPrice,
            Integer highPrice,
            Integer lowPrice,
            Integer closePrice,
            Long volume,
            Double changeRate,
            Integer previousClose
    ) {
        StockPriceHistory history = new StockPriceHistory();
        history.stock = stock;
        history.tradeDate = tradeDate;
        history.openPrice = openPrice;
        history.highPrice = highPrice;
        history.lowPrice = lowPrice;
        history.closePrice = closePrice;
        history.volume = volume;
        history.changeRate = changeRate;
        history.previousClose = previousClose;
        return history;
    }
}
