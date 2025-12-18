package io.fundic.fundic_server.application.sector;

import io.fundic.fundic_server.domain.SectorScore;
import io.fundic.fundic_server.domain.SectorSnapshot;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SectorAllocationService {

    public record Selection(String leader, String support, String buffer) {}

    public Selection allocate(List<SectorScore> scores, List<SectorSnapshot> snapshots) {
        Map<String, SectorSnapshot> snapMap = snapshots.stream()
                .collect(Collectors.toMap(SectorSnapshot::getSectorId, s -> s));

        // 1) leader
        String leader = scores.stream()
                .max(Comparator.comparingInt(SectorScore::getTotalScore))
                .orElseThrow().getSectorId();

        // 2) support: leader와 corr 너무 높으면 제외
        String support = scores.stream()
                .filter(s -> !s.getSectorId().equals(leader))
                .filter(s -> corr(snapMap, leader, s.getSectorId()) <= 0.75)
                .max(Comparator.comparingInt(SectorScore::getTotalScore))
                .orElseGet(() -> fallbackSecond(scores, leader))
                .getSectorId();

        // 3) buffer: riskScore 상위 중 leader/support와 corr 제한
        String buffer = scores.stream()
                .filter(s -> !Set.of(leader, support).contains(s.getSectorId()))
                .sorted(Comparator.comparingInt(SectorScore::getRiskScore).reversed())
                .filter(s -> corr(snapMap, leader, s.getSectorId()) <= 0.60)
                .filter(s -> corr(snapMap, support, s.getSectorId()) <= 0.60)
                .findFirst()
                .orElseGet(() -> fallbackByRisk(scores, leader, support))
                .getSectorId();

        return new Selection(leader, support, buffer);
    }

    public Map<String, List<String>> swapOptions(List<SectorScore> scores, List<SectorSnapshot> snapshots, Selection sel) {
        Map<String, SectorSnapshot> snapMap = snapshots.stream()
                .collect(Collectors.toMap(SectorSnapshot::getSectorId, s -> s));

        List<String> leaderCandidates = scores.stream()
                .sorted(Comparator.comparingInt(SectorScore::getTotalScore).reversed())
                .map(SectorScore::getSectorId)
                .filter(id -> !Set.of(sel.leader(), sel.support(), sel.buffer()).contains(id))
                .limit(3).toList();

        List<String> supportCandidates = scores.stream()
                .sorted(Comparator.comparingInt(SectorScore::getTotalScore).reversed())
                .map(SectorScore::getSectorId)
                .filter(id -> !Set.of(sel.leader(), sel.support(), sel.buffer()).contains(id))
                .filter(id -> corr(snapMap, sel.leader(), id) <= 0.75)
                .limit(3).toList();

        List<String> bufferCandidates = scores.stream()
                .sorted(Comparator.comparingInt(SectorScore::getRiskScore).reversed())
                .map(SectorScore::getSectorId)
                .filter(id -> !Set.of(sel.leader(), sel.support(), sel.buffer()).contains(id))
                .filter(id -> corr(snapMap, sel.leader(), id) <= 0.60)
                .filter(id -> corr(snapMap, sel.support(), id) <= 0.60)
                .limit(3).toList();

        return Map.of("leader", leaderCandidates, "support", supportCandidates, "buffer", bufferCandidates);
    }

    private double corr(Map<String, SectorSnapshot> snapMap, String a, String b) {
        if (a.equals(b)) return 1.0;
        SectorSnapshot sa = snapMap.get(a);
        if (sa == null || sa.getCorrelation() == null) return 0.0;
        return sa.getCorrelation().getOrDefault(b, 0.0);
    }

    private SectorScore fallbackSecond(List<SectorScore> scores, String leader) {
        return scores.stream()
                .filter(s -> !s.getSectorId().equals(leader))
                .sorted(Comparator.comparingInt(SectorScore::getTotalScore).reversed())
                .findFirst().orElseThrow();
    }

    private SectorScore fallbackByRisk(List<SectorScore> scores, String leader, String support) {
        return scores.stream()
                .filter(s -> !Set.of(leader, support).contains(s.getSectorId()))
                .sorted(Comparator.comparingInt(SectorScore::getRiskScore).reversed())
                .findFirst().orElseThrow();
    }
}
