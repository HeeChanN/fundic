package io.fundic.fundic_server.application;

import io.fundic.fundic_server.infrastructure.sector.SectorRepository;
import io.fundic.fundic_server.presentation.dto.SectorDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SectorService {

    private final SectorRepository sectorRepository;

    @Transactional(readOnly = true)
    public List<SectorDto> getSectors() {
        return sectorRepository.findAll().stream()
                .map(s -> new SectorDto(s.getId(), s.getName()))
                .toList();
    }
}
