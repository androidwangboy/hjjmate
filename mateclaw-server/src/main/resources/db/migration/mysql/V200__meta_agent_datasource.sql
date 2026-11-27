-- 修改人:祝奔；时间：2026-11-16T14:09:29.021Z；修改原因/内容：新增专家与数据源多选绑定关系表；代码来源：AI生成
CREATE TABLE IF NOT EXISTS mate_agent_datasource (
                                                     id              BIGINT       NOT NULL PRIMARY KEY,
                                                     agent_id        BIGINT       NOT NULL,
                                                     datasource_id   BIGINT       NOT NULL,
                                                     create_time     DATETIME     NOT NULL,
                                                     update_time     DATETIME     NOT NULL,
                                                     deleted         INT          NOT NULL DEFAULT 0,
                                                     INDEX idx_agent_datasource_agent (agent_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
