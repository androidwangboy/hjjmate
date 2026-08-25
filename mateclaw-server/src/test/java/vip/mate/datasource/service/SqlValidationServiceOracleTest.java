package vip.mate.datasource.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vip.mate.exception.MateClawException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Oracle 11g/12c/19c 兼容的 SQL 验证与归一化测试。
 * <p>
 * 验证的核心不变式：任何通过验证的 Oracle SQL 都必须被包装成
 * {@code SELECT * FROM (...) WHERE ROWNUM <= N}，且以 500 为行数上限。
 */
class SqlValidationServiceOracleTest {

    private final SqlValidationService service = new SqlValidationService();

    @Test
    @DisplayName("普通 SELECT 用 ROWNUM 外层包装代替 LIMIT")
    void plainSelectGetsRowNumWrap() {
        String result = service.validateAndNormalize("SELECT * FROM ORDERS", "oracle");
        assertEquals("SELECT * FROM (SELECT * FROM ORDERS) WHERE ROWNUM <= 500", result);
    }

    @Test
    @DisplayName("Oracle 常用函数与方言（SYSDATE/TO_CHAR/||/NVL）可解析且被包装")
    void oracleFunctionsRemainQueryable() {
        String sql = "SELECT TO_CHAR(SYSDATE, 'YYYY') AS Y, 'a' || 'b' AS AB, NVL(AMT, 0) AS AMT FROM DUAL";
        String result = service.validateAndNormalize(sql, "oracle");
        assertTrue(result.startsWith("SELECT * FROM ("));
        assertTrue(result.endsWith(") WHERE ROWNUM <= 500"));
        assertTrue(result.contains("TO_CHAR"));
        assertTrue(result.contains("NVL"));
    }

    @Test
    @DisplayName("用户写的 LIMIT 被剥离并作为 ROWNUM 上限（不超过 500）")
    void userLimitBecomesRowNumCap() {
        String result = service.validateAndNormalize("SELECT * FROM ORDERS LIMIT 100", "oracle");
        assertEquals("SELECT * FROM (SELECT * FROM ORDERS) WHERE ROWNUM <= 100", result);

        String capped = service.validateAndNormalize("SELECT * FROM ORDERS LIMIT 5000", "oracle");
        assertEquals("SELECT * FROM (SELECT * FROM ORDERS) WHERE ROWNUM <= 500", capped);
    }

    @Test
    @DisplayName("带 ORDER BY 的查询保留内层排序（ROWNUM 在外层，语义与 LIMIT 一致）")
    void orderByStaysInsideRowNumWrap() {
        String result = service.validateAndNormalize(
                "SELECT * FROM ORDERS ORDER BY CREATE_TIME DESC", "oracle");
        assertEquals(
                "SELECT * FROM (SELECT * FROM ORDERS ORDER BY CREATE_TIME DESC) WHERE ROWNUM <= 500",
                result);
    }

    @Test
    @DisplayName("WITH 子句外提到 ROWNUM 包装之外（Oracle 11g 不支持子查询内 WITH）")
    void withClauseIsHoistedOutsideRowNumWrap() {
        String result = service.validateAndNormalize(
                "WITH T AS (SELECT 1 AS X FROM DUAL) SELECT X FROM T", "oracle");
        assertTrue(result.startsWith("WITH "), "WITH 必须外提到最外层: " + result);
        assertTrue(result.contains("AS (SELECT 1 AS X FROM DUAL)"), "WITH 定义必须保留: " + result);
        assertTrue(result.endsWith(") WHERE ROWNUM <= 500"), "ROWNUM 包装必须在尾部: " + result);
        assertFalse(result.contains("WITH") && result.indexOf("WITH") > 0, "WITH 只能出现在开头: " + result);
    }

    @Test
    @DisplayName("UNION 集合查询整体被 ROWNUM 包装")
    void setOperationsGetRowNumWrap() {
        String result = service.validateAndNormalize(
                "SELECT A FROM T1 UNION SELECT B FROM T2", "oracle");
        assertEquals(
                "SELECT * FROM (SELECT A FROM T1 UNION SELECT B FROM T2) WHERE ROWNUM <= 500",
                result);
    }

    @Test
    @DisplayName("写操作（INSERT/UPDATE/DELETE）仍被拒绝")
    void writeStatementsStillRejected() {
        assertThrows(MateClawException.class,
                () -> service.validateAndNormalize("INSERT INTO ORDERS VALUES (1)", "oracle"));
        assertThrows(MateClawException.class,
                () -> service.validateAndNormalize("UPDATE ORDERS SET A = 1", "oracle"));
        assertThrows(MateClawException.class,
                () -> service.validateAndNormalize("DELETE FROM ORDERS", "oracle"));
    }

    @Test
    @DisplayName("多语句注入仍被拒绝")
    void multiStatementStillRejected() {
        assertThrows(MateClawException.class,
                () -> service.validateAndNormalize(
                        "SELECT * FROM ORDERS; DROP TABLE ORDERS", "oracle"));
    }

    @Test
    @DisplayName("dbType 为 null 时保持非 Oracle 行为（注入 LIMIT 500）")
    void nullDbTypeKeepsLegacyLimitBehavior() {
        String result = service.validateAndNormalize("SELECT * FROM ORDERS", null);
        assertTrue(result.contains("LIMIT 500"), "非 Oracle 必须注入 LIMIT: " + result);
        assertTrue(result.startsWith("SELECT * FROM ORDERS"), "非 Oracle 不应包装: " + result);
    }

    @Test
    @DisplayName("Oracle 数据字典级别的 SELECT（dual 查询）可包装")
    void dualQueryGetsRowNumWrap() {
        String result = service.validateAndNormalize("SELECT 1 FROM DUAL", "oracle");
        assertEquals("SELECT * FROM (SELECT 1 FROM DUAL) WHERE ROWNUM <= 500", result);
    }
}
