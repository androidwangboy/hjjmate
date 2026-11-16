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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
