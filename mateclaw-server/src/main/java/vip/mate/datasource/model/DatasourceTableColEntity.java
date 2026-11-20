// 修改人:祝奔；时间：2026-11-19T09:02:06.584Z；修改原因/内容：新增数据源表字段配置实体类；代码来源：AI生成
package vip.mate.datasource.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源表字段配置（字段自定义描述与启用状态落库）
 */
@Data
@TableName("mate_datasource_table_col")
public class DatasourceTableColEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 数据源 ID */
    private Long datasourceId;

    /** 表配置 ID（mate_datasource_table.id） */
    private Long tableId;

    /** 表名 */
    private String tableName;

    /** 字段名称 */
    private String columnName;

    /** 字段类型 */
    private String columnType;

    /** 数据库原始字段描述 */
    private String columnComment;

    /** 自定义描述 */
    private String customComment;

    /** 是否启用 */
    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
