package vip.mate.agent.api.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Per-agent publication and default governance settings for the public API. */
@Data
@TableName("mate_agent_api_publication")
public class AgentApiPublicationEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long agentId;
    private Long workspaceId;
    private Boolean enabled;

    @TableField("model_alias")
    private String modelAlias;

    private Integer requestsPerMinute;
    private Integer concurrentLimit;
    private Integer dailyQuota;
    private Integer timeoutSeconds;
    private Boolean webhookEnabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
