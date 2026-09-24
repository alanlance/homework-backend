
CREATE TABLE IF NOT EXISTS rate_limit_rules (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    api_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    request_limit INT UNSIGNED NOT NULL,
    window_seconds INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_rate_limit_rules_api_key (api_key),
    CONSTRAINT chk_rate_limit_rules_request_limit CHECK (request_limit > 0),
    CONSTRAINT chk_rate_limit_rules_window_seconds CHECK (window_seconds > 0)
);

CREATE TABLE IF NOT EXISTS rate_limit_exceeded_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(36) NOT NULL,
    api_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    usage_count BIGINT UNSIGNED NOT NULL,
    request_limit INT UNSIGNED NOT NULL,
    window_seconds INT UNSIGNED NOT NULL,
    window_ttl INT UNSIGNED NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_rate_limit_exceeded_events_event_id (event_id),
    KEY idx_rate_limit_exceeded_events_api_key_occurred_at (api_key, occurred_at)
);
