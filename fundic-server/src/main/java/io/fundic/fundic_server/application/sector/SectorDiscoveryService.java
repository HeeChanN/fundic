package io.fundic.fundic_server.application.sector;

import io.fundic.fundic_server.domain.SectorSnapshot;
import io.fundic.fundic_server.presentation.dto.UserProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SectorDiscoveryService {

    private final SectorKeywordDictionary dictionary;

    // 후보 섹터 최소 개수(너무 적으면 추천 품질/분산이 떨어짐)
    private static final int MIN_CANDIDATES = 6;

    public List<SectorSnapshot> filterCandidates(List<SectorSnapshot> universe, UserProfileRequest req) {
        List<String> keywords = req.getThemeKeywords();

        if (keywords == null || keywords.isEmpty()) {
            return universe; // 키워드 없으면 전체 후보
        }

        Set<String> picked = new LinkedHashSet<>();

        // 1) 키워드 직접 매핑
        for (String kw : keywords) {
            picked.addAll(dictionary.sectorsForKeyword(kw));
        }

        // 2) 연관 섹터 1단 확장
        Set<String> expand = new LinkedHashSet<>();
        for (String sector : picked) {
            expand.addAll(dictionary.relatedForSector(sector));
        }
        picked.addAll(expand);

        // 3) universe에 존재하는 섹터만 남김
        Set<String> universeIds = universe.stream()
                .map(SectorSnapshot::getSectorId)
                .collect(Collectors.toSet());

        List<SectorSnapshot> filtered = universe.stream()
                .filter(s -> picked.contains(s.getSectorId()))
                .toList();

        // 4) 후보가 너무 적으면 fallback: 전체로 확대(또는 부분 확대)
        if (filtered.size() < MIN_CANDIDATES) {
            return universe;
        }
        return filtered;
    }
}
