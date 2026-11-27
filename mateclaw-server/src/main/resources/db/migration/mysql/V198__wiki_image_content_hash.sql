-- 修改人:祝奔；时间：2026-11-20T06:14:14.191Z；修改原因/内容：wiki 图片按内容 hash 去重，新增 content_hash 字段与唯一索引；代码来源：AI生成
ALTER TABLE mate_wiki_image ADD COLUMN content_hash VARCHAR(64) NULL COMMENT '图片内容 SHA-256，用于 workspace+kb 范围内去重' AFTER source_ref;
ALTER TABLE mate_wiki_image ADD UNIQUE INDEX uk_wiki_image_ws_kb_hash (workspace_id, kb_id, content_hash);
