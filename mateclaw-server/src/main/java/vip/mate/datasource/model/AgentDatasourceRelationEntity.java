package vip.mate.datasource.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务聚合
 *
 * @version [V2.0]
 * @package vip.mate.datasource.model;
 * @description:[专家与数据元绑定关系]
 * @author: [zhuben]
 * @date: [2026-11-16 13:41]
 * @copyright: 2026 echovin.com
 */
@Data
@TableName("mate_agent_datasource")
public class AgentDatasourceRelationEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long agentId;

    private Long datasourceId;

    // 修改人:祝奔；时间：2026-11-23T02:15:04.591Z；修改原因/内容：新增导入批次字段，支持撤销导入时删除本批次创建的专家数据源绑定；代码来源：AI生成
    private String importBatch;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
