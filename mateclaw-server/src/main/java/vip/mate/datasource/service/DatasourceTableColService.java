// 修改人:祝奔；时间：2026-11-19T09:02:06.584Z；修改原因/内容：新增表字段集合查询服务，支持五种数据库且合并落库配置；代码来源：AI生成
package vip.mate.datasource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.datasource.model.DatasourceColumnVO;
import vip.mate.datasource.model.DatasourceEntity;
import vip.mate.datasource.model.DatasourceTableColEntity;
import vip.mate.datasource.repository.DatasourceTableColMapper;
import vip.mate.exception.MateClawException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部数据源字段元数据查询服务
 * <p>
 * 字段名/类型/描述来自目标数据库系统视图，自定义描述与启用状态来自
 * mate_datasource_table_col 落库配置（未匹配时为空 / 不启用）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceTableColService {

    private final DatasourceService datasourceService;
    private final DatasourceConnectionManager connectionManager;
    private final DatasourceTableColMapper colMapper;

    /**
     * 根据数据源 ID 与表名查询字段集合
     */
    public List<DatasourceColumnVO> listColumns(Long datasourceId, String tableName) {
        DatasourceEntity entity = datasourceService.getDecrypted(datasourceId);
        // 修改人:祝奔；时间：2026-11-19T09:02:06.584Z；修改原因/内容：按数据源与表名读取字段落库配置；代码来源：AI生成
        Map<String, DatasourceTableColEntity> configMap = new HashMap<>();
        List<DatasourceTableColEntity> configs = colMapper.selectList(
                new LambdaQueryWrapper<DatasourceTableColEntity>()
                        .eq(DatasourceTableColEntity::getDatasourceId, datasourceId)
                        .eq(DatasourceTableColEntity::getTableName, tableName)
                        .eq(DatasourceTableColEntity::getDeleted, 0));
        configs.forEach(c -> configMap.put(c.getColumnName(), c));

        String dbType = entity.getDbType() == null ? "" : entity.getDbType().toLowerCase();
        List<DatasourceColumnVO> result;
        try (Connection conn = connectionManager.getConnection(entity)) {
            if ("mysql".equals(dbType) || "mariadb".equals(dbType)) {
                result = queryMysqlFamily(conn, entity, tableName);
            } else if ("postgresql".equals(dbType)) {
                result = queryPostgresql(conn, entity, tableName);
            } else if ("oracle".equals(dbType)) {
                result = queryOracle(conn, entity, tableName);
            } else if ("clickhouse".equals(dbType)) {
                result = queryClickhouse(conn, entity, tableName);
            } else {
                throw new MateClawException("err.datasource.unsupported_db", "不支持的数据库类型: " + dbType);
            }
        } catch (SQLException e) {
            log.warn("查询表字段集合失败 [{}].{}: {}", entity.getName(), tableName, e.getMessage());
            throw new MateClawException("err.datasource.column_query_failed", "查询表字段失败: " + e.getMessage());
        }

        // 修改人:祝奔；时间：2026-11-19T09:02:06.584Z；修改原因/内容：自定义描述与启用状态按落库配置赋值，未匹配为空/不启用；代码来源：AI生成
        result.forEach(vo -> {
            DatasourceTableColEntity cfg = configMap.get(vo.getColumnName());
            vo.setCustomComment(cfg == null || cfg.getCustomComment() == null ? "" : cfg.getCustomComment());
            vo.setEnabled(cfg != null && Boolean.TRUE.equals(cfg.getEnabled()));
        });
        return result;
    }

    private List<DatasourceColumnVO> queryMysqlFamily(Connection conn, DatasourceEntity entity, String tableName) throws SQLException {
        String sql = "SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
        List<DatasourceColumnVO> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getDatabaseName());
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapColumn(rs));
            }
        }
        return result;
    }

    private List<DatasourceColumnVO> queryPostgresql(Connection conn, DatasourceEntity entity, String tableName) throws SQLException {
        String schema = (entity.getSchemaName() == null || entity.getSchemaName().isBlank())
                ? "public" : entity.getSchemaName();
        String sql = "SELECT a.attname AS COLUMN_NAME, " +
                "format_type(a.atttypid, a.atttypmod) AS COLUMN_TYPE, " +
                "col_description(c.oid, a.attnum) AS COLUMN_COMMENT " +
                "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace " +
                "JOIN pg_attribute a ON a.attrelid = c.oid " +
                "WHERE n.nspname = ? AND c.relname = ? AND a.attnum > 0 AND NOT a.attisdropped " +
                "ORDER BY a.attnum";
        List<DatasourceColumnVO> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapColumn(rs));
            }
        }
        return result;
    }

    private List<DatasourceColumnVO> queryOracle(Connection conn, DatasourceEntity entity, String tableName) throws SQLException {
        String owner = (entity.getSchemaName() == null || entity.getSchemaName().isBlank())
                ? (entity.getUsername() == null ? null : entity.getUsername().toUpperCase())
                : entity.getSchemaName().toUpperCase();
        String sql = "SELECT c.COLUMN_NAME AS COLUMN_NAME, c.DATA_TYPE AS COLUMN_TYPE, " +
                "cc.COMMENTS AS COLUMN_COMMENT FROM ALL_TAB_COLUMNS c " +
                "LEFT JOIN ALL_COL_COMMENTS cc ON cc.OWNER = c.OWNER " +
                "AND cc.TABLE_NAME = c.TABLE_NAME AND cc.COLUMN_NAME = c.COLUMN_NAME " +
                "WHERE c.OWNER = ? AND c.TABLE_NAME = ? ORDER BY c.COLUMN_ID";
        List<DatasourceColumnVO> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, owner);
            ps.setString(2, tableName.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapColumn(rs));
            }
        }
        return result;
    }

    private List<DatasourceColumnVO> queryClickhouse(Connection conn, DatasourceEntity entity, String tableName) throws SQLException {
        String sql = "SELECT name AS COLUMN_NAME, type AS COLUMN_TYPE, comment AS COLUMN_COMMENT " +
                "FROM system.columns WHERE database = ? AND table = ? ORDER BY position";
        List<DatasourceColumnVO> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getDatabaseName());
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapColumn(rs));
            }
        }
        return result;
    }

    private DatasourceColumnVO mapColumn(ResultSet rs) throws SQLException {
        DatasourceColumnVO vo = new DatasourceColumnVO();
        vo.setColumnName(rs.getString("COLUMN_NAME"));
        vo.setColumnType(rs.getString("COLUMN_TYPE"));
        vo.setColumnComment(rs.getString("COLUMN_COMMENT"));
        return vo;
    }
}
