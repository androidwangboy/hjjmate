package vip.mate.datasource.service;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;
import org.springframework.stereotype.Service;
import vip.mate.exception.MateClawException;

import java.util.List;
import java.util.Locale;

/**
 * SQL 安全验证服务
 * <p>
 * 仅允许 SELECT 语句，拒绝一切写操作。
 * 无 LIMIT 时自动注入 LIMIT 500（MySQL/PostgreSQL/ClickHouse 等）。
 * Oracle 11g 兼容分支：LIMIT 语法不存在，改为 ROWNUM 外层包装；
 * WITH 子句外提（Oracle 11g 不支持子查询内的 WITH）。
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
public class SqlValidationService {

    private static final long DEFAULT_LIMIT = 500;

    /**
     * 验证并处理 SQL（不指定数据库类型，按非 Oracle 方言处理）。
     *
     * @deprecated 请使用 {@link #validateAndNormalize(String, String)} 显式传入数据库类型
     */
    @Deprecated
    public String validateAndNormalize(String sql) {
        return validateAndNormalize(sql, null);
    }

    /**
     * 验证并处理 SQL：
     * 1. 仅允许单条 SELECT 语句
     * 2. 无 LIMIT 时自动注入 LIMIT 500（非 Oracle）
     * 3. Oracle 方言：LIMIT 由 ROWNUM 外层包装代替，WITH 外提，兼容 11g/12c/19c
     *
     * @param sql    原始 SQL
     * @param dbType 数据库类型（mysql / postgresql / clickhouse / oracle 等），为 null 时按非 Oracle 处理
     * @return 处理后的安全 SQL
     */
    public String validateAndNormalize(String sql, String dbType) {
        if (sql == null || sql.isBlank()) {
            throw new MateClawException("err.datasource.sql_empty", "SQL 不能为空");
        }

        // 去除末尾分号
        sql = sql.strip();
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).strip();
        }

        Statement statement;
        try {
            // 尝试解析为多条语句，检查是否有多语句注入
            Statements stmts = CCJSqlParserUtil.parseStatements(sql);
            if (stmts.getStatements().size() != 1) {
                throw new MateClawException("err.datasource.only_single_sql", "仅允许执行单条 SQL 语句，检测到 " + stmts.getStatements().size() + " 条");
            }
            statement = stmts.getStatements().get(0);
        } catch (JSQLParserException e) {
            throw new MateClawException("err.datasource.sql_parse_failed", "SQL 解析失败: " + e.getMessage());
        }

        // 仅允许 SELECT
        if (!(statement instanceof Select)) {
            throw new MateClawException("err.datasource.only_select", "仅允许 SELECT 查询，检测到: " + statement.getClass().getSimpleName());
        }

        Select select = (Select) statement;

        // Oracle 方言：ROWNUM 包装代替 LIMIT（11g 兼容）
        if (isOracle(dbType)) {
            return normalizeForOracle(select);
        }

        // 非 Oracle：注入 LIMIT 500（如果缺失）
        injectLimitIfAbsent(select);
        return select.toString();
    }

    private boolean isOracle(String dbType) {
        return dbType != null && "oracle".equalsIgnoreCase(dbType.trim());
    }

    /**
     * Oracle 11g/12c/19c 兼容的 LIMIT 归一化。
     * <p>
     * Oracle 11g 不支持 LIMIT / FETCH FIRST 语法（12c 才有），因此：
     * <ul>
     *   <li>移除 jsqlparser 解析出的 LIMIT（若用户误写），取其行数作为上限（上限仍受 500 封顶）</li>
     *   <li>WITH 子句外提到最外层——Oracle 11g 不允许子查询内嵌套 WITH</li>
     *   <li>以 {@code SELECT * FROM (...) WHERE ROWNUM <= N} 包装完成行数限制，
     *       ORDER BY 保留在内层，语义与 LIMIT 一致</li>
     * </ul>
     */
    private String normalizeForOracle(Select select) {
        // 1. 剥离用户 LIMIT 并计算行数上限
        Long userLimit = extractAndRemoveLimit(select);
        long cap = (userLimit != null && userLimit > 0)
                ? Math.min(userLimit, DEFAULT_LIMIT)
                : DEFAULT_LIMIT;

        // 2. WITH 外提
        String withClause = extractWithClause(select);

        // 3. ROWNUM 包装
        return withClause + "SELECT * FROM (" + select.toString() + ") WHERE ROWNUM <= " + cap;
    }

    /**
     * 剥离 SELECT 上的 LIMIT 并返回用户期望的行数（无法解析时返回 null）。
     */
    private Long extractAndRemoveLimit(Select select) {
        Limit limit = select.getLimit();
        if (limit == null) {
            return null;
        }
        select.setLimit(null);
        Expression rowCount = limit.getRowCount();
        if (rowCount instanceof LongValue longValue) {
            return longValue.getValue();
        }
        return null;
    }

    /**
     * 将 WITH 子句从 SELECT 上剥离并以字符串形式返回（外提前置）。
     * Oracle 11g 不允许子查询内部嵌套 WITH，所以必须先提出来。
     */
    private String extractWithClause(Select select) {
        List<WithItem<?>> items = select.getWithItemsList();
        if (items == null || items.isEmpty()) {
            return "";
        }
        select.setWithItemsList(null);
        StringBuilder sb = new StringBuilder("WITH ");
        for (int i = 0; i < items.size(); i++) {
            WithItem<?> item = items.get(i);
            String rendered = item.toString();
            if (item.isRecursive() && !rendered.toUpperCase(Locale.ROOT).startsWith("RECURSIVE")) {
                rendered = "RECURSIVE " + rendered;
            }
            sb.append(rendered);
            if (i < items.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(" ");
        return sb.toString();
    }

    private void injectLimitIfAbsent(Select select) {
        if (select instanceof PlainSelect) {
            PlainSelect plain = (PlainSelect) select;
            if (plain.getLimit() == null) {
                Limit limit = new Limit();
                limit.setRowCount(new LongValue(DEFAULT_LIMIT));
                plain.setLimit(limit);
            }
        } else if (select instanceof SetOperationList) {
            SetOperationList setOp = (SetOperationList) select;
            if (setOp.getLimit() == null) {
                Limit limit = new Limit();
                limit.setRowCount(new LongValue(DEFAULT_LIMIT));
                setOp.setLimit(limit);
            }
        }
    }
}
