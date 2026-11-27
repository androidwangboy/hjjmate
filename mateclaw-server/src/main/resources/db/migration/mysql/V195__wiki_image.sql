-- 修改人:祝奔；时间：2026-11-18T09:27:23.022Z；修改原因/内容：新增 Wiki 素材内嵌图片元数据表，支持鉴权访问与页面正文引用；代码来源：AI生成
CREATE TABLE IF NOT EXISTS mate_wiki_image (
    id              BIGINT       NOT NULL PRIMARY KEY,
    kb_id           BIGINT       NOT NULL,
    raw_id          BIGINT,
    workspace_id    BIGINT       NOT NULL DEFAULT 1,
    ext             VARCHAR(16)  NOT NULL,
    mime_type       VARCHAR(64),
    file_size       BIGINT       NOT NULL DEFAULT 0,
    source_ref      VARCHAR(256),
    create_time     DATETIME     NOT NULL,
    update_time     DATETIME     NOT NULL,
    deleted         INT          NOT NULL DEFAULT 0,
    INDEX idx_wiki_image_raw (raw_id),
    INDEX idx_wiki_image_kb (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
