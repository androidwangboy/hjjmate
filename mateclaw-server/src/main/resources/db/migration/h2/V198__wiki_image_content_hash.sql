-- 修改人:祝奔；时间：2026-11-20T06:14:14.191Z；修改原因/内容：wiki 图片按内容 hash 去重，新增 content_hash 字段与唯一索引；代码来源：AI生成
ALTER TABLE mate_wiki_image ADD COLUMN content_hash VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wiki_image_ws_kb_hash ON mate_wiki_image(workspace_id, kb_id, content_hash);
