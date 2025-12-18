package io.fundic.fundic_server.application.sector;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SectorKeywordDictionary {

    // 키워드 → 직접 매핑 섹터
    private final Map<String, Set<String>> keywordToSectors = new HashMap<>();

    // 섹터 → 연관 섹터(확장용)
    private final Map<String, Set<String>> relatedSectors = new HashMap<>();

    public SectorKeywordDictionary() {
        // ====== 예시 매핑(너 도메인에 맞게 계속 확장) ======
        map("반도체", "SEMICONDUCTOR");
        map("ai", "SEMICONDUCTOR", "IT_SERVICE");          // 예시
        map("클라우드", "IT_SERVICE");
        map("게임", "GAME");
        map("바이오", "BIO");
        map("배당", "UTILITY", "BANK", "TELECOM");
        map("금리", "BANK");
        map("소비", "CONSUMER");

        // 연관 섹터 확장(너무 많지 않게 1~2단계만)
        relate("SEMICONDUCTOR", "IT_SERVICE");
        relate("IT_SERVICE", "SEMICONDUCTOR");
        relate("BANK", "UTILITY");
        relate("UTILITY", "BANK");
    }

    private void map(String keyword, String... sectorIds) {
        keywordToSectors.computeIfAbsent(norm(keyword), k -> new HashSet<>())
                .addAll(Arrays.asList(sectorIds));
    }

    private void relate(String sector, String... related) {
        relatedSectors.computeIfAbsent(sector, k -> new HashSet<>())
                .addAll(Arrays.asList(related));
    }

    public Set<String> sectorsForKeyword(String keyword) {
        return keywordToSectors.getOrDefault(norm(keyword), Collections.emptySet());
    }

    public Set<String> relatedForSector(String sectorId) {
        return relatedSectors.getOrDefault(sectorId, Collections.emptySet());
    }

    private String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}