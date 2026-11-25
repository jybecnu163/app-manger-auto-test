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
