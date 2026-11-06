-- V195: 病历模板库新增知识库关联字段 kb_id（关联 mate_wiki_knowledge_base.id）
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_record_template' AND COLUMN_NAME = 'kb_id');
SET @s := IF(@c = 0, 'ALTER TABLE mate_wiki_record_template ADD COLUMN kb_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_record_template' AND INDEX_NAME = 'idx_rt_kb');
SET @s := IF(@c = 0, 'CREATE INDEX idx_rt_kb ON mate_wiki_record_template(kb_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
