package vip.mate.auth.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务聚合
 *
 * @version [V2.0]
 * @package vip.mate.auth.model;
 * @description:[用一句话描述该文件做什么]
 * @author: [zhuben]
 * @date: [2026-10-16 11:52]
 * @copyright: 2026 echovin.com
 */

@Data
@TableName("mate_user_primission")
public class UserPrimissionEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户名 */
    private Long userId;

    private Long agentId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
