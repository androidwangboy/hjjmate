package vip.mate.datasource.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vip.mate.datasource.model.DatasourceEntity;
import vip.mate.exception.MateClawException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DatasourceConnectionManager#buildJdbcUrl} 的 URL 构建测试，
 * 覆盖 Oracle 分支与既有数据库的回归行为。
 */
class DatasourceConnectionManagerUrlTest {

    private DatasourceEntity base(String dbType) {
        DatasourceEntity e = new DatasourceEntity();
        e.setId(1L);
        e.setDbType(dbType);
        e.setHost("10.0.0.1");
        e.setPort(1521);
        e.setDatabaseName("ORCL");
        return e;
    }

    @Test
    @DisplayName("Oracle 生成 Thin 驱动 Service Name 格式 URL")
    void oracleServiceNameUrl() {
        assertEquals("jdbc:oracle:thin:@//10.0.0.1:1521/ORCL",
                DatasourceConnectionManager.buildJdbcUrl(base("oracle")));
    }

    @Test
    @DisplayName("Oracle URL 接受 extraParams 追加（保留既有拼接规则）")
    void oracleUrlAppendsExtraParams() {
        DatasourceEntity e = base("oracle");
        e.setExtraParams("oracle.net.CONNECT_TIMEOUT=10000");
        assertEquals("jdbc:oracle:thin:@//10.0.0.1:1521/ORCL?oracle.net.CONNECT_TIMEOUT=10000",
                DatasourceConnectionManager.buildJdbcUrl(e));
    }

    @Test
    @DisplayName("Oracle 忽略 schemaName（无 currentSchema 语义，过滤在元数据层完成）")
    void oracleIgnoresSchemaInUrl() {
        DatasourceEntity e = base("oracle");
        e.setSchemaName("APP");
        String url = DatasourceConnectionManager.buildJdbcUrl(e);
        assertFalse(url.contains("currentSchema"), "Oracle URL 不应携带 currentSchema: " + url);
        assertEquals(0, url.indexOf("jdbc:oracle:thin:@//10.0.0.1:1521/ORCL"), url);
    }

    @Test
    @DisplayName("MySQL 行为回归不受影响")
    void mysqlRegression() {
        DatasourceEntity e = base("mysql");
        e.setPort(3306);
        e.setDatabaseName("shop");
        String url = DatasourceConnectionManager.buildJdbcUrl(e);
        assertTrue(url.startsWith("jdbc:mysql://10.0.0.1:3306/shop"), url);
        assertTrue(url.contains("useUnicode=true"), "MySQL 应有默认 extra: " + url);
    }

    @Test
    @DisplayName("不安全 JDBC 参数仍被拒绝（allowLoadLocalInfile）")
    void unsafeJdbcParamsRejected() {
        DatasourceEntity e = base("oracle");
        e.setExtraParams("allowLoadLocalInfile=true");
        assertThrows(MateClawException.class,
                () -> DatasourceConnectionManager.buildJdbcUrl(e));
    }

    @Test
    @DisplayName("不支持的数据库类型仍抛异常")
    void unsupportedDbTypeThrows() {
        DatasourceEntity e = base("sqlserver");
        assertThrows(MateClawException.class,
                () -> DatasourceConnectionManager.buildJdbcUrl(e));
    }
}
