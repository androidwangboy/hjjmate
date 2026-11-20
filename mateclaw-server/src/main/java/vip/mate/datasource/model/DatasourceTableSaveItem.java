// 修改人:祝奔；时间：2026-11-19T09:18:14.021Z；修改原因/内容：新增保存请求中的表配置对象；代码来源：AI生成
package vip.mate.datasource.model;

import lombok.Data;

import java.util.List;

/**
 * 保存请求中的表配置
 */
@Data
public class DatasourceTableSaveItem {

    /** 表配置 ID（新增时为空） */
    private String tableId;

    /** 表名 */
    private String tableName;

    /** 表描述 */
    private String tableComment;

    /** 来源库 / Schema */
    private String tableSchema;

    /** 表类型 */
    private String tableType;

    /** 是否启用 */
    private Boolean enabled;

    /** 字段全量集合 */
    private List<DatasourceColumnSaveItem> columns;
}
