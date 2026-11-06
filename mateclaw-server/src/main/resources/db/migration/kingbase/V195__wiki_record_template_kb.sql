-- V195: 病历模板库新增知识库关联字段 kb_id（关联 mate_wiki_knowledge_base.id）
ALTER TABLE mate_wiki_record_template ADD COLUMN IF NOT EXISTS kb_id BIGINT NULL;
CREATE INDEX IF NOT EXISTS idx_rt_kb ON mate_wiki_record_template(kb_id);
