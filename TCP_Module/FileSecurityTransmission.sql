create database db_FileSecurityTransmission;
use db_FileSecurityTransmission;


CREATE TABLE IF NOT EXISTS device (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标识每一条设备记录',
    device_id VARCHAR(64) NOT NULL COMMENT '设备唯一标识，对应 node.device-id',
    device_name VARCHAR(128) DEFAULT NULL COMMENT '设备名称，可选',
    public_key TEXT NOT NULL COMMENT '设备公钥，Base64',
    public_key_fingerprint VARCHAR(128) DEFAULT NULL COMMENT '公钥指纹，便于检索和审计',
    status VARCHAR(32) NOT NULL DEFAULT 'OFFLINE' COMMENT 'OFFLINE/ONLINE/DISABLED',
    last_seen_at DATETIME DEFAULT NULL COMMENT '最后在线时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_device_device_id (device_id),
    KEY idx_device_status (status),
    KEY idx_device_last_seen_at (last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备主表';

CREATE TABLE IF NOT EXISTS transfer_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标识每一条传输记录',
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID，对应 TransferTask.taskId',
    transfer_id VARCHAR(64) NOT NULL COMMENT '传输ID，对应 TransferTask.transferId',
    direction VARCHAR(16) NOT NULL COMMENT 'SEND/RECEIVE',
    sender_device_id VARCHAR(64) DEFAULT NULL COMMENT '发送端设备ID',
    receiver_device_id VARCHAR(64) DEFAULT NULL COMMENT '接收端设备ID',
    peer_device_id VARCHAR(64) DEFAULT NULL COMMENT '对端设备ID，兼容当前代码里的 peerDeviceId',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    -- local_path VARCHAR(1024) DEFAULT NULL COMMENT '本地文件路径',
    total_bytes BIGINT NOT NULL DEFAULT 0 COMMENT '文件总字节数',
    total_blocks INT NOT NULL DEFAULT 0 COMMENT '总分块数',
    transferred_bytes BIGINT NOT NULL DEFAULT 0 COMMENT '已传输字节数',
    transferred_blocks INT NOT NULL DEFAULT 0 COMMENT '已传输块数',
    progress DECIMAL(8,4) NOT NULL DEFAULT 0.0000 COMMENT '进度比例，0~1',
    status VARCHAR(32) NOT NULL COMMENT 'PENDING/WAITING_FOR_TARGET/WAITING_FOR_ACCEPT/TRANSFERRING/COMPLETED/FAILED/REJECTED',
    message VARCHAR(512) DEFAULT '' COMMENT '状态说明',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME DEFAULT NULL,
    completed_at DATETIME DEFAULT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_transfer_task_task_id (task_id),
    UNIQUE KEY uk_transfer_task_transfer_id (transfer_id),
    KEY idx_transfer_task_status (status),
    KEY idx_transfer_task_sender (sender_device_id),
    KEY idx_transfer_task_receiver (receiver_device_id),
    KEY idx_transfer_task_created_at (created_at),
    CONSTRAINT fk_transfer_task_sender_device
        FOREIGN KEY (sender_device_id) REFERENCES device(device_id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_transfer_task_receiver_device
        FOREIGN KEY (receiver_device_id) REFERENCES device(device_id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传输任务主表';

CREATE TABLE IF NOT EXISTS transfer_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标识每一条传输记录',
    transfer_id VARCHAR(64) NOT NULL COMMENT '传输ID',
    task_id VARCHAR(64) DEFAULT NULL COMMENT '任务ID',
    event_type VARCHAR(64) NOT NULL COMMENT 'CREATED/DEVICE_SELECTED/OFFER_SENT/ACCEPTED/REJECTED/PROGRESS/COMPLETED/FAILED',
    event_message VARCHAR(512) DEFAULT NULL COMMENT '事件描述',
    event_payload JSON DEFAULT NULL COMMENT '扩展信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_transfer_event_transfer_id (transfer_id),
    KEY idx_transfer_event_task_id (task_id),
    KEY idx_transfer_event_event_type (event_type),
    KEY idx_transfer_event_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传输事件流水表';

CREATE TABLE IF NOT EXISTS auth_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标识每一条认证记录',
    device_id VARCHAR(64) DEFAULT NULL COMMENT '认证设备ID',
    public_key_fingerprint VARCHAR(128) DEFAULT NULL COMMENT '公钥指纹',
    challenge_id VARCHAR(64) DEFAULT NULL COMMENT '挑战ID',
    client_ip VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
    result VARCHAR(16) NOT NULL COMMENT 'SUCCESS/FAILED',
    failure_reason VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_auth_log_device_id (device_id),
    KEY idx_auth_log_result (result),
    KEY idx_auth_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证日志表';







