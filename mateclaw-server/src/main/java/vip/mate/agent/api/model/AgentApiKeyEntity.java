package vip.mate.agent.api.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/** Expert-scoped API credential. Plaintext is never persisted. */
@Data
@TableName("mate_agent_api_key")
public class AgentApiKeyEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long publicationId;
    private Long agentId;
    private Long workspaceId;
    private String name;
    private String keyPrefix;

    @JsonIgnore
    private String tokenHash;

    private Boolean enabled;
    private Integer requestsPerMinuteOverride;
    private Integer concurrentLimitOverride;
    private Integer dailyQuotaOverride;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
