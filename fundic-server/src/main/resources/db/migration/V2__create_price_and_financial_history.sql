-- Stock Price History Table
CREATE TABLE stock_price_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_id BIGINT NOT NULL,
    trade_date DATE NOT NULL,
    open_price INT NOT NULL,
    high_price INT NOT NULL,
    low_price INT NOT NULL,
    close_price INT NOT NULL,
    volume BIGINT NOT NULL,
    change_rate DOUBLE NOT NULL,
    previous_close INT,
    rsi DOUBLE,
    macd DOUBLE,
    ma20 DOUBLE,
    ma60 DOUBLE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_price_history_stock FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE,
    CONSTRAINT uk_stock_date UNIQUE (stock_id, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_stock_date ON stock_price_history(stock_id, trade_date);
CREATE INDEX idx_trade_date ON stock_price_history(trade_date);

-- Financial Data History Table
CREATE TABLE financial_data_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_id BIGINT NOT NULL,
    fiscal_year INT NOT NULL,
    fiscal_quarter INT NOT NULL,
    per DOUBLE,
    pbr DOUBLE,
    roe DOUBLE,
    operating_margin DOUBLE,
    debt_ratio DOUBLE,
    market_cap BIGINT,
    eps DOUBLE,
    data_date DATE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_financial_history_stock FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE,
    CONSTRAINT uk_stock_fiscal_period UNIQUE (stock_id, fiscal_year, fiscal_quarter),
    CONSTRAINT chk_fiscal_quarter CHECK (fiscal_quarter BETWEEN 1 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_stock_quarter ON financial_data_history(stock_id, fiscal_year, fiscal_quarter);
CREATE INDEX idx_fiscal_period ON financial_data_history(fiscal_year, fiscal_quarter);
