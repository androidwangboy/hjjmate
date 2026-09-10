-- V191: import batch stamping for the portability (export / import) feature.
-- PostgreSQL / KingbaseES dialect — see h2/V191 for design notes.
-- Applied for the `postgres` profile as well (shares this migration tree).

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
