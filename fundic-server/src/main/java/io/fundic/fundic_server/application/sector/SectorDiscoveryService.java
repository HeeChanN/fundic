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
    public List<SectorSnapshot> filterCandidates(List<SectorSnapshot> universe, UserProfileRequest req) {
        return universe;
    }
}
