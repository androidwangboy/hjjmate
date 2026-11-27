-- 修改人:祝奔；时间：2026-11-19T08:20:28.733Z；修改原因/内容：新增数据源表元数据表 mate_datasource_table；代码来源：AI生成
CREATE TABLE IF NOT EXISTS mate_datasource_table (
    id              BIGINT       NOT NULL PRIMARY KEY,
    datasource_id   BIGINT       NOT NULL,
    table_name      VARCHAR(128) NOT NULL,
    table_comment   VARCHAR(512),
    -- 修改人:祝奔；时间：2026-11-19T08:49:01.621Z；修改原因/内容：新增 table_schema 与 table_type 字段；代码来源：AI生成
    table_schema    VARCHAR(128),
    table_type      VARCHAR(32),
    enabled         TINYINT(1)   NOT NULL DEFAULT 1,
    create_time     DATETIME     NOT NULL,
    update_time     DATETIME     NOT NULL,
    deleted         INT          NOT NULL DEFAULT 0,
    INDEX idx_datasource_table_ds (datasource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
