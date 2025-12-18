package io.fundic.fundic_server.global.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.util.List;

@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(
        boolean enabled,
        boolean clearExisting,
        String encoding,
        List<Resource> sectorCsvs,
        List<Resource> stockCsvs
) {}