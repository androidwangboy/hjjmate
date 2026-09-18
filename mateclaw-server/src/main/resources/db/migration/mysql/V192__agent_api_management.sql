-- V192: expert-scoped public API publication, keys, logs and tasks.

CREATE TABLE IF NOT EXISTS mate_agent_api_publication (
    id                    BIGINT       NOT NULL PRIMARY KEY,
    agent_id              BIGINT       NOT NULL,
    workspace_id          BIGINT       NOT NULL,
    enabled               BOOLEAN      DEFAULT FALSE,
    model_alias           VARCHAR(64)  DEFAULT 'expert',
    requests_per_minute   INT          DEFAULT 60,
    concurrent_limit      INT          DEFAULT 4,
    daily_quota           INT          DEFAULT 10000,
    timeout_seconds       INT          DEFAULT 120,
    webhook_enabled       BOOLEAN      DEFAULT FALSE,
    create_time           TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted               INT          DEFAULT 0,
    UNIQUE KEY uk_agent_api_publication_agent (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mate_agent_api_key (
    id                              BIGINT       NOT NULL PRIMARY KEY,
    publication_id                  BIGINT       NOT NULL,
    agent_id                        BIGINT       NOT NULL,
    workspace_id                    BIGINT       NOT NULL,
    name                            VARCHAR(128),
    key_prefix                      VARCHAR(32)  NOT NULL,
    token_hash                      CHAR(64)     NOT NULL,
    enabled                         BOOLEAN      DEFAULT TRUE,
    requests_per_minute_override    INT,
    concurrent_limit_override       INT,
    daily_quota_override            INT,
    last_used_at                    TIMESTAMP    NULL,
    revoked_at                      TIMESTAMP    NULL,
    create_time                     TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                     TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                         INT          DEFAULT 0,
    UNIQUE KEY uk_agent_api_key_hash (token_hash),
    KEY idx_agent_api_key_agent (agent_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mate_agent_api_request_log (
    id                 BIGINT       NOT NULL PRIMARY KEY,
    request_id         VARCHAR(64)  NOT NULL,
    task_id            VARCHAR(128),
    publication_id     BIGINT       NOT NULL,
    api_key_id         BIGINT       NOT NULL,
    agent_id           BIGINT       NOT NULL,
    workspace_id       BIGINT       NOT NULL,
    protocol           VARCHAR(32)  NOT NULL,
    mode               VARCHAR(32)  NOT NULL,
    conversation_id    VARCHAR(255),
    end_user_id        VARCHAR(255),
    status             VARCHAR(32)  NOT NULL,
    http_status        INT,
    error_code         VARCHAR(128),
    latency_ms         BIGINT,
    input_tokens       INT,
    output_tokens      INT,
    create_time        TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_agent_api_log_agent_time (agent_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mate_agent_api_task (
    id                 BIGINT       NOT NULL PRIMARY KEY,
    task_id            VARCHAR(128) NOT NULL,
    request_id         VARCHAR(64)  NOT NULL,
    publication_id     BIGINT       NOT NULL,
    api_key_id         BIGINT       NOT NULL,
    agent_id           BIGINT       NOT NULL,
    workspace_id       BIGINT       NOT NULL,
    conversation_id    VARCHAR(255) NOT NULL,
    end_user_id        VARCHAR(255) NOT NULL,
    status             VARCHAR(32)  NOT NULL,
    result_text        LONGTEXT,
    error_message      LONGTEXT,
    callback_url       VARCHAR(2048),
    callback_status    VARCHAR(32),
    started_at         TIMESTAMP    NULL,
    completed_at       TIMESTAMP    NULL,
    expires_at         TIMESTAMP    NULL,
    create_time        TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted            INT          DEFAULT 0,
    UNIQUE KEY uk_agent_api_task_task_id (task_id),
    KEY idx_agent_api_task_agent_time (agent_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
