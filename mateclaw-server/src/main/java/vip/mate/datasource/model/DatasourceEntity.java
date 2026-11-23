package vip.mate.datasource.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部数据源实体（查数功能）
 *
 * @author MateClaw Team
 */
@Data
@TableName("mate_datasource")
public class DatasourceEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 数据源名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 数据库类型：mysql / postgresql / clickhouse */
    private String dbType;

    /** 主机地址 */
    private String host;

    /** 端口 */
    private Integer port;

    /** 数据库名称 */
    private String databaseName;

    /** 用户名 */
    private String username;

    /** 密码（AES 加密存储） */
    private String password;

    /** JDBC URL 附加参数 */
    private String extraParams;

    /** PostgreSQL schema */
    private String schemaName;

    /** 是否启用 */
    private Boolean enabled;

    /** 最近测试时间 */
    private LocalDateTime lastTestTime;

    /** 最近测试结果 */
    private Boolean lastTestOk;

    // 修改人:祝奔；时间：2026-11-23T02:15:04.591Z；修改原因/内容：新增导入批次字段，支持可移植包撤销导入时精准删除本批次创建的数据源；代码来源：AI生成
    private String importBatch;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
