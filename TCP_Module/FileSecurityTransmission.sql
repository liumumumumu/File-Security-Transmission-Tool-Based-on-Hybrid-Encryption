create database db_FileSecurityTransmission;
use db_FileSecurityTransmission;
show tables;

CREATE TABLE IF NOT EXISTS device (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id VARCHAR(64) NOT NULL,
    public_key TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OFFLINE',
    last_seen_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_device_device_id (device_id),
    KEY idx_device_status (status),
    KEY idx_device_last_seen_at (last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS auth_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id VARCHAR(64) DEFAULT NULL,
    public_key TEXT DEFAULT NULL,
    challenge_id VARCHAR(64) DEFAULT NULL,
    client_ip VARCHAR(64) DEFAULT NULL,
    result VARCHAR(16) NOT NULL,
    failure_reason VARCHAR(512) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_auth_log_device_id (device_id),
    KEY idx_auth_log_result (result),
    KEY idx_auth_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


