package io.fundic.fundic_server.presentation;


import io.fundic.fundic_server.application.SectorService;
import io.fundic.fundic_server.presentation.dto.SectorDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SectorController {

    private final SectorService sectorService;

    @GetMapping("/sectors")
    public List<SectorDto> sectors() {
        return sectorService.getSectors();
    }
}
