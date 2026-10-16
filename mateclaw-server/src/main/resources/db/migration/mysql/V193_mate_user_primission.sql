CREATE TABLE IF NOT EXISTS mate_user_primission (
    id          BIGINT       NOT NULL PRIMARY KEY,
    user_id     BIGINT  NOT NULL,
    agent_id    BIGINT NOT NULL,
    create_time           TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted               INT          DEFAULT 0,
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
