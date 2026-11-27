-- 修改人:祝奔；时间：2026-11-23T02:15:04.591Z；修改原因/内容：为数据源、专家数据源绑定、数据源表与字段配置增加导入批次字段，支持可移植包撤销导入；代码来源：AI生成

ALTER TABLE mate_datasource ADD COLUMN IF NOT EXISTS import_batch VARCHAR(64);
ALTER TABLE mate_agent_datasource ADD COLUMN IF NOT EXISTS import_batch VARCHAR(64);
ALTER TABLE mate_datasource_table ADD COLUMN IF NOT EXISTS import_batch VARCHAR(64);
ALTER TABLE mate_datasource_table_col ADD COLUMN IF NOT EXISTS import_batch VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_datasource_import_batch ON mate_datasource (import_batch);
CREATE INDEX IF NOT EXISTS idx_agent_datasource_import_batch ON mate_agent_datasource (import_batch);
CREATE INDEX IF NOT EXISTS idx_datasource_table_import_batch ON mate_datasource_table (import_batch);
CREATE INDEX IF NOT EXISTS idx_datasource_table_col_import_batch ON mate_datasource_table_col (import_batch);
