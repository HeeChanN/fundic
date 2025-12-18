package io.fundic.fundic_server.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(
        name = "sectors",
        uniqueConstraints = @UniqueConstraint(name = "uk_sector_name", columnNames = "name")
)
public class Sector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name; // 업종명

    @OneToMany(mappedBy = "sector", fetch = FetchType.LAZY)
    private List<Stock> stocks = new ArrayList<>();

    public Sector(String name) {
        this.name = name;
    }

    public static Sector of(String name) {
        return new Sector(name.trim());
    }
}
