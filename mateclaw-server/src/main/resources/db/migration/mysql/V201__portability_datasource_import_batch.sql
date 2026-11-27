-- 修改人:祝奔；时间：2026-11-23T02:15:04.591Z；修改原因/内容：为数据源、专家数据源绑定、数据源表与字段配置增加导入批次字段，支持可移植包撤销导入；代码来源：AI生成
-- MySQL 无 ADD COLUMN IF NOT EXISTS，沿用 V191 的 INFORMATION_SCHEMA 守卫写法。

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_datasource'
             AND COLUMN_NAME = 'import_batch');
SET @s := IF(@c = 0, 'ALTER TABLE mate_datasource ADD COLUMN import_batch VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent_datasource'
             AND COLUMN_NAME = 'import_batch');
SET @s := IF(@c = 0, 'ALTER TABLE mate_agent_datasource ADD COLUMN import_batch VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_datasource_table'
             AND COLUMN_NAME = 'import_batch');
SET @s := IF(@c = 0, 'ALTER TABLE mate_datasource_table ADD COLUMN import_batch VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_datasource_table_col'
             AND COLUMN_NAME = 'import_batch');
SET @s := IF(@c = 0, 'ALTER TABLE mate_datasource_table_col ADD COLUMN import_batch VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @i := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_datasource'
             AND INDEX_NAME = 'idx_datasource_import_batch');
SET @s := IF(@i = 0, 'CREATE INDEX idx_datasource_import_batch ON mate_datasource (import_batch)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @i := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent_datasource'
             AND INDEX_NAME = 'idx_agent_datasource_import_batch');
SET @s := IF(@i = 0, 'CREATE INDEX idx_agent_datasource_import_batch ON mate_agent_datasource (import_batch)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @i := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_datasource_table'
             AND INDEX_NAME = 'idx_datasource_table_import_batch');
SET @s := IF(@i = 0, 'CREATE INDEX idx_datasource_table_import_batch ON mate_datasource_table (import_batch)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @i := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_datasource_table_col'
             AND INDEX_NAME = 'idx_datasource_table_col_import_batch');
SET @s := IF(@i = 0, 'CREATE INDEX idx_datasource_table_col_import_batch ON mate_datasource_table_col (import_batch)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
