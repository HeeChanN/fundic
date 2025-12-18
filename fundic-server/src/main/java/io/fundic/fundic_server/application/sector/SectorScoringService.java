package io.fundic.fundic_server.application.sector;

import io.fundic.fundic_server.application.WeightPolicy;
import io.fundic.fundic_server.domain.SectorScore;
import io.fundic.fundic_server.domain.SectorSnapshot;
import io.fundic.fundic_server.presentation.dto.UserProfileRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

@Service
public class SectorScoringService {

    public List<SectorScore> score(List<SectorSnapshot> snapshots, UserProfileRequest profile) {
        var w = WeightPolicy.from(profile.getRisk(), profile.getGoal());

        // 정규화 범위(후보 내 min-max)
        var m1MinMax = minMax(snapshots, SectorSnapshot::getRet1m);
        var m3MinMax = minMax(snapshots, SectorSnapshot::getRet3m);
        var trMinMax = minMax(snapshots, SectorSnapshot::getTrend20d);

        var volMinMax = minMax(snapshots, SectorSnapshot::getVol20d);
        var mddMinMax = minMax(snapshots, SectorSnapshot::getMdd60d); // -0.2 ~ -0.03 같은 값

        // event는 (pos - neg)로 단순화
        var evMinMax = minMax(snapshots, s -> (double) (s.getPosNews() - s.getNegNews()));

        return snapshots.stream().map(s -> {
            int momentumScore = round(
                    0.25 * norm(s.getRet1m(), m1MinMax.min, m1MinMax.max) +
                            0.50 * norm(s.getRet3m(), m3MinMax.min, m3MinMax.max) +
                            0.25 * norm(s.getTrend20d(), trMinMax.min, trMinMax.max)
            );

            // Risk: vol 낮을수록 좋고, mdd 덜 빠질수록 좋음
            int riskScore = round(
                    0.60 * invNorm(s.getVol20d(), volMinMax.min, volMinMax.max) +
                            0.40 * invNorm(s.getMdd60d(), mddMinMax.min, mddMinMax.max) // mdd는 더 음수일수록 나쁨
            );

            int eventScore = round(norm((s.getPosNews() - s.getNegNews()), evMinMax.min, evMinMax.max));

            // diversifyScore는 allocator에서 상관을 보고 계산할 게 더 맞지만
            // MVP에선 "기본값 50"로 두고 allocator에서 최종 조합으로 보정 가능
            int diversifyScore = 50;

            int total = round(
                    w.momentum() * momentumScore +
                            w.risk() * riskScore +
                            w.diversify() * diversifyScore +
                            w.event() * eventScore
            );

            return SectorScore.builder()
                    .sectorId(s.getSectorId())
                    .momentumScore(momentumScore)
                    .riskScore(riskScore)
                    .eventScore(eventScore)
                    .diversifyScore(diversifyScore)
                    .totalScore(total)
                    .build();
        }).toList();
    }

    private record MinMax(double min, double max) {}

    private MinMax minMax(List<SectorSnapshot> s, ToDoubleFunction<SectorSnapshot> f) {
        double min = s.stream().min(Comparator.comparingDouble(f)).map(f::applyAsDouble).orElse(0.0);
        double max = s.stream().max(Comparator.comparingDouble(f)).map(f::applyAsDouble).orElse(0.0);
        return new MinMax(min, max);
    }

    private double norm(double v, double min, double max) {
        if (max - min < 1e-9) return 50;
        return 100.0 * (v - min) / (max - min);
    }

    private double invNorm(double v, double min, double max) {
        // 낮을수록 좋음
        return 100.0 - norm(v, min, max);
    }

    private int round(double v) {
        return (int) Math.round(Math.max(0, Math.min(100, v)));
    }
}