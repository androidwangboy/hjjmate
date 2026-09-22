package vip.mate.agent.api.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Metadata-only audit row for one external API request. */
@Data
@TableName("mate_agent_api_request_log")
public class AgentApiRequestLogEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String requestId;
    private String taskId;
    private Long publicationId;
    private Long apiKeyId;
    private Long agentId;
    private Long workspaceId;
    private String protocol;
    private String mode;
    private String conversationId;
    private String endUserId;
    private String status;
    private Integer httpStatus;
    private String errorCode;
    private Long latencyMs;
    private Integer inputTokens;
    private Integer outputTokens;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
