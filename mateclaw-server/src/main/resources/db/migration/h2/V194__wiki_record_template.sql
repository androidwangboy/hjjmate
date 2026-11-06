-- V194: 病历模板库（方案 B：独立维护，不挂 Wiki KB）
CREATE TABLE IF NOT EXISTS mate_wiki_record_template (
    id                    BIGINT        NOT NULL PRIMARY KEY,
    name                  VARCHAR(255)  NOT NULL,
    source_type           VARCHAR(32)   NOT NULL,
    file_name             VARCHAR(512),
    file_path             VARCHAR(1024),
    workspace_id          BIGINT        NOT NULL,
    status                VARCHAR(32)   NOT NULL DEFAULT 'draft',
    style_signature       CLOB,
    style_hash            VARCHAR(64)   NOT NULL,
    style_embedding       BLOB,
    embedding_model       VARCHAR(128),
    embedding_text_version VARCHAR(32),
    layout_type           VARCHAR(32),
    field_count           INT,
    table_count           INT,
    section_count         INT,
    content_json          CLOB,
    ext_attrs_json        CLOB,
    deleted               INT           NOT NULL DEFAULT 0,
    create_time           DATETIME      NOT NULL,
    update_time           DATETIME      NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_rt_ws_hash ON mate_wiki_record_template(workspace_id, style_hash);
CREATE INDEX IF NOT EXISTS idx_rt_ws   ON mate_wiki_record_template(workspace_id);
CREATE INDEX IF NOT EXISTS idx_rt_type ON mate_wiki_record_template(source_type);
