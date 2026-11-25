CREATE TABLE IF NOT EXISTS app (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    env VARCHAR(64) NOT NULL DEFAULT 'default',
    token VARCHAR(128) NOT NULL,
    interfaces_hash VARCHAR(64),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_app_name_env (name, env)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id VARCHAR(64) NOT NULL,
    instance_key VARCHAR(255) NOT NULL,
    ip VARCHAR(64) NOT NULL,
    port INT NOT NULL,
    hostname VARCHAR(128),
    status VARCHAR(16) NOT NULL DEFAULT 'UP',
    last_heartbeat_at DATETIME NOT NULL,
    start_time BIGINT,
    UNIQUE KEY uk_instance (app_id, instance_key),
    KEY idx_app_heartbeat (app_id, last_heartbeat_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS api_interface (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id VARCHAR(64) NOT NULL,
    method VARCHAR(16) NOT NULL,
    path VARCHAR(512) NOT NULL,
    controller VARCHAR(255),
    operation VARCHAR(255),
    request_schema TEXT,
    response_schema TEXT,
    schema_hash VARCHAR(64),
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_interface (app_id, method, path),
    KEY idx_app_interface (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS test_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id VARCHAR(64) NOT NULL,
    interface_id BIGINT,
    request_summary TEXT,
    response_status INT,
    cost_ms BIGINT,
    created_at DATETIME NOT NULL,
    KEY idx_app_test (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS api_test_case (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id VARCHAR(64) NOT NULL,
    interface_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    path_params TEXT,
    query_params TEXT,
    headers TEXT,
    body TEXT,
    content_hash VARCHAR(64) NOT NULL,
    source VARCHAR(16) NOT NULL DEFAULT 'USER',
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_test_case_hash (interface_id, content_hash),
    KEY idx_case_interface (interface_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS traffic_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id VARCHAR(64) NOT NULL,
    interface_id BIGINT,
    instance_key VARCHAR(255),
    trace_id VARCHAR(128),
    method VARCHAR(16) NOT NULL,
    path VARCHAR(512) NOT NULL,
    query_params TEXT,
    headers TEXT,
    body TEXT,
    response_status INT,
    response_body TEXT,
    cost_ms BIGINT,
    success TINYINT NOT NULL DEFAULT 1,
    error TEXT,
    request_hash VARCHAR(64),
    created_at DATETIME NOT NULL,
    KEY idx_traffic_app_time (app_id, created_at),
    KEY idx_traffic_interface_time (interface_id, created_at),
    KEY idx_traffic_trace (trace_id),
    KEY idx_traffic_hash (request_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS external_call_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(128),
    method VARCHAR(16) NOT NULL,
    url VARCHAR(1024) NOT NULL,
    request_headers TEXT,
    request_body TEXT,
    response_status INT,
    response_headers TEXT,
    response_body TEXT,
    cost_ms BIGINT,
    request_hash VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    KEY idx_external_app_time (app_id, created_at),
    KEY idx_external_trace (trace_id),
    KEY idx_external_hash (request_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mock_response (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id VARCHAR(64) NOT NULL,
    interface_id BIGINT,
    method VARCHAR(16) NOT NULL,
    url_pattern VARCHAR(1024) NOT NULL,
    match_mode VARCHAR(16) NOT NULL DEFAULT 'EXACT',
    request_hash VARCHAR(64),
    mock_status INT NOT NULL DEFAULT 200,
    mock_headers TEXT,
    mock_body TEXT,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_mock_app_hash (app_id, request_hash),
    KEY idx_mock_app_interface (app_id, interface_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_interface' AND COLUMN_NAME = 'write_operation'), 'SELECT 1', 'ALTER TABLE api_interface ADD COLUMN write_operation TINYINT NOT NULL DEFAULT 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'test_log' AND COLUMN_NAME = 'replay_id'), 'SELECT 1', 'ALTER TABLE test_log ADD COLUMN replay_id VARCHAR(64)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'test_log' AND COLUMN_NAME = 'replay_mode'), 'SELECT 1', 'ALTER TABLE test_log ADD COLUMN replay_mode VARCHAR(16) NOT NULL DEFAULT ''NONE''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'test_log' AND COLUMN_NAME = 'db_rolled_back'), 'SELECT 1', 'ALTER TABLE test_log ADD COLUMN db_rolled_back TINYINT NOT NULL DEFAULT 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'test_log' AND COLUMN_NAME = 'mock_hits'), 'SELECT 1', 'ALTER TABLE test_log ADD COLUMN mock_hits INT NOT NULL DEFAULT 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'test_log' AND COLUMN_NAME = 'mock_misses'), 'SELECT 1', 'ALTER TABLE test_log ADD COLUMN mock_misses INT NOT NULL DEFAULT 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
