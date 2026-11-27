CREATE TABLE IF NOT EXISTS mate_user_primission (
    id          BIGINT       NOT NULL PRIMARY KEY,
    user_id     BIGINT  NOT NULL,
    agent_id    BIGINT NOT NULL,
    create_time DATETIME     NOT NULL,
    update_time DATETIME     NOT NULL,
    deleted     INT          NOT NULL DEFAULT 0
    );

CREATE INDEX IF NOT EXISTS ix_mateuserprimission_userId
    ON mate_user_primission(user_id);

CREATE INDEX IF NOT EXISTS ix_mateuserprimission_agentId
    ON mate_user_primission(agent_id);