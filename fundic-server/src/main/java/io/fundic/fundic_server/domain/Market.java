package io.fundic.fundic_server.domain;

import java.util.Locale;

public enum Market {
    KOSPI,
    KOSDAQ;

    public static Market from(String v) {
        if (v == null) {
            throw new IllegalArgumentException("market is null");
        }
        return Market.valueOf(v.trim().toUpperCase(Locale.ROOT));
    }
}
