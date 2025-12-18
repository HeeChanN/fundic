package io.fundic.fundic_server.infrastructure.sector;

import io.fundic.fundic_server.domain.SectorSnapshot;
import io.fundic.fundic_server.domain.SectorSnapshotProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class StubSectorSnapshotProvider implements SectorSnapshotProvider {

    @Override
    public List<SectorSnapshot> loadUniverse() {
        // MVP: 샘플 6개 섹터만. 실제로는 CSV/DB/KIS로 교체.
        LocalDate asOf = LocalDate.now();

        // corr map은 "대칭"이 이상적이지만 MVP에선 필요한 방향만 채워도 됨.
        SectorSnapshot semi = SectorSnapshot.builder()
                .sectorId("SEMICONDUCTOR").asOf(asOf)
                .ret1m(0.04).ret3m(0.12).trend20d(0.8)
                .vol20d(0.28).mdd60d(-0.14)
                .posNews(12).negNews(5)
                .correlation(mapOf("BANK", 0.35, "UTILITY", 0.18, "BIO", 0.62, "GAME", 0.55, "CONSUMER", 0.22))
                .build();

        SectorSnapshot bank = SectorSnapshot.builder()
                .sectorId("BANK").asOf(asOf)
                .ret1m(0.02).ret3m(0.06).trend20d(0.4)
                .vol20d(0.16).mdd60d(-0.07)
                .posNews(7).negNews(3)
                .correlation(mapOf("SEMICONDUCTOR", 0.35, "UTILITY", 0.25, "BIO", 0.20, "GAME", 0.15, "CONSUMER", 0.30))
                .build();

        SectorSnapshot util = SectorSnapshot.builder()
                .sectorId("UTILITY").asOf(asOf)
                .ret1m(0.01).ret3m(0.03).trend20d(0.2)
                .vol20d(0.10).mdd60d(-0.04)
                .posNews(4).negNews(2)
                .correlation(mapOf("SEMICONDUCTOR", 0.18, "BANK", 0.25, "BIO", 0.10, "GAME", 0.05, "CONSUMER", 0.35))
                .build();

        SectorSnapshot bio = SectorSnapshot.builder()
                .sectorId("BIO").asOf(asOf)
                .ret1m(-0.01).ret3m(0.04).trend20d(0.1)
                .vol20d(0.32).mdd60d(-0.18)
                .posNews(6).negNews(8)
                .correlation(mapOf("SEMICONDUCTOR", 0.62, "BANK", 0.20, "UTILITY", 0.10, "GAME", 0.40, "CONSUMER", 0.12))
                .build();

        SectorSnapshot game = SectorSnapshot.builder()
                .sectorId("GAME").asOf(asOf)
                .ret1m(0.03).ret3m(0.08).trend20d(0.6)
                .vol20d(0.30).mdd60d(-0.16)
                .posNews(9).negNews(6)
                .correlation(mapOf("SEMICONDUCTOR", 0.55, "BANK", 0.15, "UTILITY", 0.05, "BIO", 0.40, "CONSUMER", 0.20))
                .build();

        SectorSnapshot cons = SectorSnapshot.builder()
                .sectorId("CONSUMER").asOf(asOf)
                .ret1m(0.015).ret3m(0.045).trend20d(0.3)
                .vol20d(0.12).mdd60d(-0.05)
                .posNews(5).negNews(2)
                .correlation(mapOf("SEMICONDUCTOR", 0.22, "BANK", 0.30, "UTILITY", 0.35, "BIO", 0.12, "GAME", 0.20))
                .build();

        return List.of(semi, bank, util, bio, game, cons);
    }

    private Map<String, Double> mapOf(Object... kv) {
        Map<String, Double> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Double) kv[i + 1]);
        }
        return m;
    }
}
