-- V194: 病历模板库（方案 B：独立维护，不挂 Wiki KB）
-- 绑定语义：样式(style_*) + 内容(content_json) + 扩展属性(ext_attrs_json) 同表同行。
CREATE TABLE IF NOT EXISTS mate_wiki_record_template (
    id                    BIGINT        NOT NULL PRIMARY KEY,
    name                  VARCHAR(255)  NOT NULL,
    source_type           VARCHAR(32)   NOT NULL,
    file_name             VARCHAR(512),
    file_path             VARCHAR(1024),
    workspace_id          BIGINT        NOT NULL,
    status                VARCHAR(32)   NOT NULL DEFAULT 'draft',
    style_signature       LONGTEXT,
    style_hash            VARCHAR(64)   NOT NULL,
    style_embedding       BLOB,
    embedding_model       VARCHAR(128),
    embedding_text_version VARCHAR(32),
    layout_type           VARCHAR(32),
    field_count           INT,
    table_count           INT,
    section_count         INT,
    content_json          LONGTEXT,
    ext_attrs_json        LONGTEXT,
    deleted               INT           NOT NULL DEFAULT 0,
    create_time           DATETIME      NOT NULL,
    update_time           DATETIME      NOT NULL
);

-- MySQL lacks CREATE INDEX IF NOT EXISTS; use INFORMATION_SCHEMA guard.
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_record_template' AND INDEX_NAME = 'uk_rt_ws_hash');
SET @s := IF(@c = 0, 'CREATE UNIQUE INDEX uk_rt_ws_hash ON mate_wiki_record_template(workspace_id, style_hash)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_record_template' AND INDEX_NAME = 'idx_rt_ws');
SET @s := IF(@c = 0, 'CREATE INDEX idx_rt_ws ON mate_wiki_record_template(workspace_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_record_template' AND INDEX_NAME = 'idx_rt_type');
SET @s := IF(@c = 0, 'CREATE INDEX idx_rt_type ON mate_wiki_record_template(source_type)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
