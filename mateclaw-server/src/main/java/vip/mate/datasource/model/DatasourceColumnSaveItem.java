// 修改人:祝奔；时间：2026-11-19T09:18:14.021Z；修改原因/内容：新增保存请求中的字段配置对象；代码来源：AI生成
package vip.mate.datasource.model;

import lombok.Data;

/**
 * 保存请求中的字段配置
 */
@Data
public class DatasourceColumnSaveItem {

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
}
