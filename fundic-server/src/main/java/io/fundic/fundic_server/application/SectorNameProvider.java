package io.fundic.fundic_server.application;

import io.fundic.fundic_server.domain.Sector;
import io.fundic.fundic_server.infrastructure.sector.SectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 섹터 ID로부터 섹터명을 조회하는 Provider
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SectorNameProvider {

    private final SectorRepository sectorRepository;

    /**
     * 섹터 ID로 섹터명 조회
     *
     * @param sectorId 섹터 ID (String)
     * @return 섹터명 (Optional)
     */
    public Optional<String> findNameById(String sectorId) {
        try {
            Long id = Long.parseLong(sectorId);
            return sectorRepository.findById(id)
                    .map(Sector::getName);
        } catch (NumberFormatException e) {
            log.warn("Invalid sector ID format: {}", sectorId);
            return Optional.empty();
        }
    }
}
