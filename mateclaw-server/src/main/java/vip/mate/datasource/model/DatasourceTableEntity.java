// 修改人:祝奔；时间：2026-11-19T08:49:01.621Z；修改原因/内容：新增数据源表配置实体类；代码来源：AI生成
package vip.mate.datasource.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源表配置（表字段维护的落库配置）
 */
@Data
@TableName("mate_datasource_table")
public class DatasourceTableEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 数据源 ID */
    private Long datasourceId;

    /** 表名 */
    private String tableName;

    /** 表描述 */
    private String tableComment;

    /** 来源库 / Schema */
    private String tableSchema;

    /** 表类型 */
    private String tableType;

    /** 是否启用（是否使用该表） */
    private Boolean enabled;

    // 修改人:祝奔；时间：2026-11-23T02:15:04.591Z；修改原因/内容：新增导入批次字段，支持撤销导入时删除本批次创建的数据源表配置；代码来源：AI生成
    private String importBatch;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
