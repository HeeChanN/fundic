package io.fundic.fundic_server.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(
        name = "stocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_code", columnNames = "code")
)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code",nullable = false, length = 20)
    private String code; // 종목코드

    @Column(name = "name", nullable = false, length = 200)
    private String name; // 종목명

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Market market;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sector_id")
    private Sector sector;

    private Stock(String code, String name, Market market, Sector sector) {
        this.code = code;
        this.name = name;
        this.market = market;
        this.sector = sector;
    }

    public static Stock of(String code, String name, Market market, Sector sector) {
        return new Stock(code, name, market, sector);
    }

    public void update(String name, Market market, Sector sector) {
        this.name = name;
        this.market = market;
        this.sector = sector;
    }
}
