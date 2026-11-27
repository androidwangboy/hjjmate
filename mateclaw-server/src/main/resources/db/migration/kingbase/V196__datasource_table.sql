-- 修改人:祝奔；时间：2026-11-19T08:20:28.733Z；修改原因/内容：新增数据源表元数据表 mate_datasource_table；代码来源：AI生成
CREATE TABLE IF NOT EXISTS mate_datasource_table (
    id              BIGINT       NOT NULL PRIMARY KEY,
    datasource_id   BIGINT       NOT NULL,
    table_name      VARCHAR(128) NOT NULL,
    table_comment   VARCHAR(512),
    -- 修改人:祝奔；时间：2026-11-19T08:49:01.621Z；修改原因/内容：新增 table_schema 与 table_type 字段；代码来源：AI生成
    table_schema    VARCHAR(128),
    table_type      VARCHAR(32),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    create_time     TIMESTAMP    NOT NULL,
    update_time     TIMESTAMP    NOT NULL,
    deleted         INT          NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_datasource_table_ds ON mate_datasource_table(datasource_id);
