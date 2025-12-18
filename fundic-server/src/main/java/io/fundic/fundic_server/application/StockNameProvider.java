package io.fundic.fundic_server.application;

import java.util.Optional;

public interface StockNameProvider {
    Optional<String> findNameByCode(String code);
}