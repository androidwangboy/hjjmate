-- V191: import batch stamping for the portability (export / import) feature.
-- MySQL dialect — no `ADD COLUMN IF NOT EXISTS`, so guard each ALTER with an
-- INFORMATION_SCHEMA lookup. See h2/V191 for design notes.

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent'
             AND COLUMN_NAME = 'import_batch');
SET @s := IF(@c = 0, 'ALTER TABLE mate_agent ADD COLUMN import_batch VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent_team'
             AND COLUMN_NAME = 'import_batch');
SET @s := IF(@c = 0, 'ALTER TABLE mate_agent_team ADD COLUMN import_batch VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_skill'
             AND COLUMN_NAME = 'import_batch');
SET @s := IF(@c = 0, 'ALTER TABLE mate_skill ADD COLUMN import_batch VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_knowledge_base'
             AND COLUMN_NAME = 'import_batch');
SET @s := IF(@c = 0, 'ALTER TABLE mate_wiki_knowledge_base ADD COLUMN import_batch VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_workflow'
             AND COLUMN_NAME = 'import_batch');
SET @s := IF(@c = 0, 'ALTER TABLE mate_workflow ADD COLUMN import_batch VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_trigger'
             AND COLUMN_NAME = 'import_batch');
SET @s := IF(@c = 0, 'ALTER TABLE mate_trigger ADD COLUMN import_batch VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Indexes: MySQL has no CREATE INDEX IF NOT EXISTS either; these are cheap
-- enough to attempt and ignore with a guarded procedure-less approach — the
-- duplicate-index error is tolerated by Flyway's MySQL dialect only when the
-- statement is idempotent, so we keep the index creation in the H2/PG trees
-- and create them here inside the same guard pattern via a helper table check.
SET @i := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent'
             AND INDEX_NAME = 'idx_agent_import_batch');
SET @s := IF(@i = 0, 'CREATE INDEX idx_agent_import_batch ON mate_agent (import_batch)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @i := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_agent_team'
             AND INDEX_NAME = 'idx_team_import_batch');
SET @s := IF(@i = 0, 'CREATE INDEX idx_team_import_batch ON mate_agent_team (import_batch)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @i := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_skill'
             AND INDEX_NAME = 'idx_skill_import_batch');
SET @s := IF(@i = 0, 'CREATE INDEX idx_skill_import_batch ON mate_skill (import_batch)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @i := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_wiki_knowledge_base'
             AND INDEX_NAME = 'idx_wiki_kb_import_batch');
SET @s := IF(@i = 0, 'CREATE INDEX idx_wiki_kb_import_batch ON mate_wiki_knowledge_base (import_batch)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @i := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_workflow'
             AND INDEX_NAME = 'idx_workflow_import_batch');
SET @s := IF(@i = 0, 'CREATE INDEX idx_workflow_import_batch ON mate_workflow (import_batch)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @i := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mate_trigger'
             AND INDEX_NAME = 'idx_trigger_import_batch');
SET @s := IF(@i = 0, 'CREATE INDEX idx_trigger_import_batch ON mate_trigger (import_batch)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
