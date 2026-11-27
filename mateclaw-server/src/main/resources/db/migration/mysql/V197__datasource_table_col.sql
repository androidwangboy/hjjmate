-- 修改人:祝奔；时间：2026-11-19T09:02:06.584Z；修改原因/内容：新增数据源表字段配置表 mate_datasource_table_col；代码来源：AI生成
CREATE TABLE IF NOT EXISTS mate_datasource_table_col (
    id              BIGINT       NOT NULL PRIMARY KEY,
    datasource_id   BIGINT       NOT NULL,
    table_id        BIGINT       NOT NULL,
    table_name      VARCHAR(128) NOT NULL,
    column_name     VARCHAR(128) NOT NULL,
    column_type     VARCHAR(64),
    column_comment  VARCHAR(512),
    custom_comment  VARCHAR(512),
    enabled         TINYINT(1)   NOT NULL DEFAULT 0,
    create_time     DATETIME     NOT NULL,
    update_time     DATETIME     NOT NULL,
    deleted         INT          NOT NULL DEFAULT 0,
    INDEX idx_dts_col_ds_table (datasource_id, table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
