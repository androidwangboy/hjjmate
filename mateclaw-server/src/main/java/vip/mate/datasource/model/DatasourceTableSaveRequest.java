// 修改人:祝奔；时间：2026-11-19T09:18:14.021Z；修改原因/内容：新增表及字段配置保存请求对象；代码来源：AI生成
package vip.mate.datasource.model;

import lombok.Data;

import java.util.List;

/**
 * 表及字段配置保存请求（tables 仅包含启用的表）
 */
@Data
public class DatasourceTableSaveRequest {

    /** 数据源 ID */
    private String datasourceId;

    /** 启用的表集合 */
    private List<DatasourceTableSaveItem> tables;
}
