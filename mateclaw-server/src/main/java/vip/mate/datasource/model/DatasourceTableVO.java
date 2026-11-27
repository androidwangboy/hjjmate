// 修改人:祝奔；时间：2026-11-19T08:31:37.101Z；修改原因/内容：新增数据源表集合接口返回对象；代码来源：AI生成
package vip.mate.datasource.model;

import lombok.Data;

/**
 * 外部数据源的表元数据（供表字段维护页面展示）
 */
@Data
public class DatasourceTableVO {

    /** 表名 */
    private String tableName;

    /** 表描述（数据库原始注释） */
    private String tableComment;

    /** 来源库 / Schema / Owner */
    private String tableSchema;

    /** 对象类型：TABLE / VIEW */
    private String tableType;

    /** 数据源配置的数据库名 */
    private String databaseName;

    /** 数据库类型 */
    private String dbType;

    // 修改人:祝奔；时间：2026-11-19T08:49:01.621Z；修改原因/内容：新增落库配置的表启用状态；代码来源：AI生成
    /** 是否启用（落库配置，未匹配到为 false） */
    private Boolean enabled;
}
