-- 修改人:祝奔；时间：2026-11-16T14:09:29.021Z；修改原因/内容：新增专家与数据源多选绑定关系表；代码来源：AI生成
CREATE TABLE IF NOT EXISTS mate_agent_datasource (
                                                     id              BIGINT       NOT NULL PRIMARY KEY,
                                                     agent_id        BIGINT       NOT NULL,
                                                     datasource_id   BIGINT       NOT NULL,
                                                     create_time     TIMESTAMP     NOT NULL,
                                                     update_time     TIMESTAMP     NOT NULL,
                                                     deleted         INT          NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_agent_datasource_agent ON mate_agent_datasource(agent_id);
