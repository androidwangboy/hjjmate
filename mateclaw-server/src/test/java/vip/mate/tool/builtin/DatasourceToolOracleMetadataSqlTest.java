package vip.mate.tool.builtin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vip.mate.datasource.model.DatasourceEntity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DatasourceTool#buildListTablesSql}/{@link DatasourceTool#buildDescribeTableSql}
 * 的 Oracle 元数据查询生成测试（不依赖真实数据库，只验证生成的 SQL 结构）。
 */
class DatasourceToolOracleMetadataSqlTest {

    private DatasourceEntity oracle(String schema) {
        DatasourceEntity e = new DatasourceEntity();
        e.setId(1L);
        e.setDbType("oracle");
        e.setHost("10.0.0.1");
        e.setPort(1521);
        e.setDatabaseName("ORCL");
        e.setSchemaName(schema);
        return e;
    }

    @Test
    @DisplayName("Oracle 表列表使用 ALL_TABLES + ALL_TAB_COMMENTS 且排除系统 schema")
    void listTablesUsesAllTablesAndExcludesSystemSchemas() {
        String sql = DatasourceTool.buildListTablesSql(oracle(null));
        assertTrue(sql.contains("ALL_TABLES"), sql);
        assertTrue(sql.contains("ALL_TAB_COMMENTS"), sql);
        assertTrue(sql.contains("NOT IN"), "必须排除系统 schema: " + sql);
        assertTrue(sql.contains("'SYS'") && sql.contains("'SYSTEM'"), sql);
        assertTrue(sql.contains("ORDER BY t.OWNER, t.TABLE_NAME"), sql);
    }

    @Test
    @DisplayName("指定 schemaName 时按 owner 精确过滤")
    void listTablesFiltersByOwnerWhenSchemaSet() {
        String sql = DatasourceTool.buildListTablesSql(oracle("APP"));
        assertTrue(sql.contains("AND t.OWNER = UPPER('APP')"), sql);
    }

    @Test
    @DisplayName("Oracle 表结构使用 ALL_TAB_COLUMNS + ALL_COL_COMMENTS + 主键合并")
    void describeTableUsesAllTabColumnsWithPkJoin() {
        String sql = DatasourceTool.buildDescribeTableSql(oracle("APP"), "ORDERS");
        assertTrue(sql.contains("ALL_TAB_COLUMNS"), sql);
        assertTrue(sql.contains("ALL_COL_COMMENTS"), sql);
        assertTrue(sql.contains("ALL_CONSTRAINTS") && sql.contains("ALL_CONS_COLUMNS"), sql);
        assertTrue(sql.contains("CONSTRAINT_TYPE = 'P'"), "必须标记主键: " + sql);
        assertTrue(sql.contains("UPPER('ORDERS')"), "表名应大写匹配: " + sql);
        assertTrue(sql.contains("AND c.OWNER = UPPER('APP')"), sql);
        assertTrue(sql.contains("ORDER BY c.COLUMN_ID"), sql);
    }

    @Test
    @DisplayName("OWNER.TABLE 前缀的表名在未指定 schema 时自动拆分 owner")
    void describeTableSplitsOwnerPrefix() {
        String sql = DatasourceTool.buildDescribeTableSql(oracle(null), "APP.ORDERS");
        assertTrue(sql.contains("k.OWNER = UPPER('APP')"), "主键子查询应按 owner 过滤: " + sql);
        assertTrue(sql.contains("c.OWNER = UPPER('APP')"), "列查询应按 owner 过滤: " + sql);
        assertTrue(sql.contains("UPPER('ORDERS')"), sql);
    }

    @Test
    @DisplayName("非法标识符仍被 sanitize 拒绝")
    void unsafeIdentifierRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DatasourceTool.buildDescribeTableSql(oracle("APP"), "ORDERS; DROP TABLE X"));
    }
}
