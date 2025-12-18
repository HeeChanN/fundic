package io.fundic.fundic_server.domain;

import java.util.List;

public interface SectorSnapshotProvider {
    List<SectorSnapshot> loadUniverse();
}
