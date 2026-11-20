// 修改人:祝奔；时间：2026-11-19T09:02:06.584Z；修改原因/内容：新增表字段集合接口返回对象；代码来源：AI生成
package vip.mate.datasource.model;

import lombok.Data;

/**
 * 外部数据源字段元数据 + 落库配置
 */
@Data
public class DatasourceColumnVO {

    /** 字段名称 */
    private String columnName;

    /** 字段类型 */
    private String columnType;

    /** 数据库原始字段描述 */
    private String columnComment;

    /** 自定义描述（落库配置，未匹配为空） */
    private String customComment;

    /** 是否启用（落库配置，未匹配为 false） */
    private Boolean enabled;
}
