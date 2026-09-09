-- V191: import batch stamping for the portability (export / import) feature.
--
-- Every row created by a `.mcbundle` import carries the bundle's batch id so
-- the operator can revert exactly that import: `revert` deletes only rows
-- whose import_batch = :batchId, and can therefore never touch pre-existing
-- data. Columns are nullable — rows created normally (or before this
-- migration) simply have no batch.
--
-- Companion files: mysql/V191 (INFORMATION_SCHEMA guard + ALTER),
-- kingbase/V191 (ADD COLUMN IF NOT EXISTS).

ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS import_batch VARCHAR(64);
ALTER TABLE mate_agent_team ADD COLUMN IF NOT EXISTS import_batch VARCHAR(64);
ALTER TABLE mate_skill ADD COLUMN IF NOT EXISTS import_batch VARCHAR(64);
ALTER TABLE mate_wiki_knowledge_base ADD COLUMN IF NOT EXISTS import_batch VARCHAR(64);
ALTER TABLE mate_workflow ADD COLUMN IF NOT EXISTS import_batch VARCHAR(64);
ALTER TABLE mate_trigger ADD COLUMN IF NOT EXISTS import_batch VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_agent_import_batch ON mate_agent (import_batch);
CREATE INDEX IF NOT EXISTS idx_team_import_batch ON mate_agent_team (import_batch);
CREATE INDEX IF NOT EXISTS idx_skill_import_batch ON mate_skill (import_batch);
CREATE INDEX IF NOT EXISTS idx_wiki_kb_import_batch ON mate_wiki_knowledge_base (import_batch);
CREATE INDEX IF NOT EXISTS idx_workflow_import_batch ON mate_workflow (import_batch);
CREATE INDEX IF NOT EXISTS idx_trigger_import_batch ON mate_trigger (import_batch);
