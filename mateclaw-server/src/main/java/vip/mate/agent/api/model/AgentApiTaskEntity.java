package vip.mate.agent.api.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Durable asynchronous external API task. */
@Data
@TableName("mate_agent_api_task")
public class AgentApiTaskEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String taskId;
    private String requestId;
    private Long publicationId;
    private Long apiKeyId;
    private Long agentId;
    private Long workspaceId;
    private String conversationId;
    private String endUserId;
    private String status;
    private String resultText;

    @TableField("error_message")
    private String errorMessage;

    @TableField("callback_url")
    private String callbackUrl;

    @TableField("callback_status")
    private String callbackStatus;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime expiresAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
