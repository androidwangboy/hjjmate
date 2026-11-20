// 修改人:祝奔；时间：2026-11-19T08:31:37.101Z；修改原因/内容：新增数据源表集合查询服务，支持 MySQL/Oracle/PostgreSQL/MariaDB/ClickHouse；代码来源：AI生成
package vip.mate.datasource.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.datasource.model.DatasourceColumnSaveItem;
import vip.mate.datasource.model.DatasourceEntity;
import vip.mate.datasource.model.DatasourceTableColEntity;
import vip.mate.datasource.model.DatasourceTableEntity;
import vip.mate.datasource.model.DatasourceTableSaveItem;
import vip.mate.datasource.model.DatasourceTableSaveRequest;
import vip.mate.datasource.model.DatasourceTableVO;
import vip.mate.datasource.repository.DatasourceTableColMapper;
import vip.mate.datasource.repository.DatasourceTableMapper;
import vip.mate.exception.MateClawException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 外部数据源表元数据查询服务
 * <p>
 * 根据数据源类型使用对应的系统视图读取表清单，避免依赖
 * DatabaseMetaData#getTables 在各数据库上 REMARKS 不一致的问题。
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceTableService {

    private final DatasourceService datasourceService;
    private final DatasourceConnectionManager connectionManager;
    private final DatasourceTableMapper datasourceTableMapper;
    private final DatasourceTableColMapper colMapper;

    /**
     * 根据数据源 ID 查询表集合
     */
    public List<DatasourceTableVO> listTables(Long datasourceId) {
        DatasourceEntity entity = datasourceService.getDecrypted(datasourceId);
        String dbType = entity.getDbType() == null ? "" : entity.getDbType().toLowerCase();
        // 修改人:祝奔；时间：2026-11-19T10:03:25.085Z；修改原因/内容：读取落库表配置，表描述以落库非空值为准，启用状态未匹配默认不启用；代码来源：AI生成
        Map<String, DatasourceTableEntity> configMap = new HashMap<>();
        List<DatasourceTableEntity> configs = datasourceTableMapper.selectList(
                new LambdaQueryWrapper<DatasourceTableEntity>()
                        .eq(DatasourceTableEntity::getDatasourceId, datasourceId)
                        .eq(DatasourceTableEntity::getDeleted, 0));
        configs.forEach(c -> configMap.put(c.getTableName(), c));
        List<DatasourceTableVO> tables;
        try (Connection conn = connectionManager.getConnection(entity)) {
            // 修改人:祝奔；时间：2026-11-19T08:34:53.175Z；修改原因/内容：改为兼容的分支写法以适配不同 JDK 编译环境；代码来源：AI生成
            if ("mysql".equals(dbType) || "mariadb".equals(dbType)) {
                tables = queryMysqlFamily(conn, entity, configMap);
            } else if ("postgresql".equals(dbType)) {
                tables = queryPostgresql(conn, entity, configMap);
            } else if ("oracle".equals(dbType)) {
                tables = queryOracle(conn, entity, configMap);
            } else if ("clickhouse".equals(dbType)) {
                tables = queryClickhouse(conn, entity, configMap);
            } else {
                throw new MateClawException("err.datasource.unsupported_db", "不支持的数据库类型: " + dbType);
            }
        } catch (SQLException e) {
            log.warn("查询数据源表集合失败 [{}]: {}", entity.getName(), e.getMessage());
            throw new MateClawException("err.datasource.table_query_failed", "查询表集合失败: " + e.getMessage());
        }
        // 修改人:祝奔；时间：2026-11-20T02:54:41.285Z；修改原因/内容：表集合排序：启用表在前，组内按表名字母序；代码来源：AI生成
        tables.sort(Comparator
                .comparing((DatasourceTableVO vo) -> !Boolean.TRUE.equals(vo.getEnabled()))
                .thenComparing(vo -> vo.getTableName() == null ? "" : vo.getTableName(), String.CASE_INSENSITIVE_ORDER));
        return tables;
    }

    private List<DatasourceTableVO> queryMysqlFamily(Connection conn, DatasourceEntity entity, Map<String, DatasourceTableEntity> configMap) throws SQLException {
        String sql = "SELECT TABLE_NAME, TABLE_COMMENT, TABLE_SCHEMA, TABLE_TYPE " +
                "FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME";
        List<DatasourceTableVO> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getDatabaseName());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DatasourceTableVO vo = new DatasourceTableVO();
                    vo.setTableName(rs.getString("TABLE_NAME"));
                    vo.setTableComment(rs.getString("TABLE_COMMENT"));
                    vo.setTableSchema(rs.getString("TABLE_SCHEMA"));
                    vo.setTableType(normalizeTableType(rs.getString("TABLE_TYPE")));
                    fillCommon(vo, entity, configMap);
                    result.add(vo);
                }
            }
        }
        return result;
    }

    private List<DatasourceTableVO> queryPostgresql(Connection conn, DatasourceEntity entity, Map<String, DatasourceTableEntity> configMap) throws SQLException {
        String schema = (entity.getSchemaName() == null || entity.getSchemaName().isBlank())
                ? "public" : entity.getSchemaName();
        String sql = "SELECT c.relname AS TABLE_NAME, " +
                "obj_description(c.oid, 'pg_class') AS TABLE_COMMENT, " +
                "n.nspname AS TABLE_SCHEMA, " +
                "CASE WHEN c.relkind IN ('v','m') THEN 'VIEW' ELSE 'TABLE' END AS TABLE_TYPE " +
                "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace " +
                "WHERE n.nspname = ? AND c.relkind IN ('r','p','v','m') ORDER BY c.relname";
        List<DatasourceTableVO> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DatasourceTableVO vo = new DatasourceTableVO();
                    vo.setTableName(rs.getString("TABLE_NAME"));
                    vo.setTableComment(rs.getString("TABLE_COMMENT"));
                    vo.setTableSchema(rs.getString("TABLE_SCHEMA"));
                    vo.setTableType(normalizeTableType(rs.getString("TABLE_TYPE")));
                    fillCommon(vo, entity, configMap);
                    result.add(vo);
                }
            }
        }
        return result;
    }

    private List<DatasourceTableVO> queryOracle(Connection conn, DatasourceEntity entity, Map<String, DatasourceTableEntity> configMap) throws SQLException {
        String owner = (entity.getSchemaName() == null || entity.getSchemaName().isBlank())
                ? (entity.getUsername() == null ? null : entity.getUsername().toUpperCase())
                : entity.getSchemaName().toUpperCase();
        String sql = "SELECT o.OBJECT_NAME AS TABLE_NAME, c.COMMENTS AS TABLE_COMMENT, " +
                "o.OWNER AS TABLE_SCHEMA, o.OBJECT_TYPE AS TABLE_TYPE " +
                "FROM ALL_OBJECTS o LEFT JOIN ALL_TAB_COMMENTS c " +
                "ON c.OWNER = o.OWNER AND c.TABLE_NAME = o.OBJECT_NAME " +
                "WHERE o.OWNER = ? AND o.OBJECT_TYPE IN ('TABLE','VIEW') ORDER BY o.OBJECT_NAME";
        List<DatasourceTableVO> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DatasourceTableVO vo = new DatasourceTableVO();
                    vo.setTableName(rs.getString("TABLE_NAME"));
                    vo.setTableComment(rs.getString("TABLE_COMMENT"));
                    vo.setTableSchema(rs.getString("TABLE_SCHEMA"));
                    vo.setTableType(normalizeTableType(rs.getString("TABLE_TYPE")));
                    fillCommon(vo, entity, configMap);
                    result.add(vo);
                }
            }
        }
        return result;
    }

    private List<DatasourceTableVO> queryClickhouse(Connection conn, DatasourceEntity entity, Map<String, DatasourceTableEntity> configMap) throws SQLException {
        String sql = "SELECT name AS TABLE_NAME, comment AS TABLE_COMMENT, database AS TABLE_SCHEMA " +
                "FROM system.tables WHERE database = ? ORDER BY name";
        List<DatasourceTableVO> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getDatabaseName());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DatasourceTableVO vo = new DatasourceTableVO();
                    vo.setTableName(rs.getString("TABLE_NAME"));
                    vo.setTableComment(rs.getString("TABLE_COMMENT"));
                    vo.setTableSchema(rs.getString("TABLE_SCHEMA"));
                    vo.setTableType("TABLE");
                    fillCommon(vo, entity, configMap);
                    result.add(vo);
                }
            }
        }
        return result;
    }

    // 修改人:祝奔；时间：2026-11-19T08:49:01.621Z；修改原因/内容：按表名填充落库启用状态，未匹配到默认不启用；代码来源：AI生成
    // 修改人:祝奔；时间：2026-11-19T10:03:25.085Z；修改原因/内容：表描述以落库非空值为准，为空时保留数据库原始描述；代码来源：AI生成
    private void fillCommon(DatasourceTableVO vo, DatasourceEntity entity, Map<String, DatasourceTableEntity> configMap) {
        vo.setDatabaseName(entity.getDatabaseName());
        vo.setDbType(entity.getDbType());
        DatasourceTableEntity config = configMap.get(vo.getTableName());
        vo.setEnabled(config != null && Boolean.TRUE.equals(config.getEnabled()));
        if (config != null && config.getTableComment() != null && !config.getTableComment().isBlank()) {
            vo.setTableComment(config.getTableComment());
        }
    }

    private String normalizeTableType(String type) {
        if (type == null) return "TABLE";
        return type.toUpperCase().contains("VIEW") ? "VIEW" : "TABLE";
    }

    // 修改人:祝奔；时间：2026-11-19T09:18:14.021Z；修改原因/内容：新增表及字段配置批量保存，按启用表与字段全量做差量同步；代码来源：AI生成
    @Transactional(rollbackFor = Exception.class)
    public void saveTables(DatasourceTableSaveRequest request) {
        if (request == null || request.getDatasourceId() == null || request.getDatasourceId().isBlank()) {
            throw new MateClawException("err.datasource.invalid_param", "数据源 ID 不能为空");
        }
        Long datasourceId;
        try {
            datasourceId = Long.valueOf(request.getDatasourceId());
        } catch (NumberFormatException e) {
            throw new MateClawException("err.datasource.invalid_param", "数据源 ID 格式错误");
        }
        List<DatasourceTableSaveItem> items = request.getTables() == null
                ? Collections.emptyList() : request.getTables();

        List<DatasourceTableEntity> existingTables = datasourceTableMapper.selectList(
                new LambdaQueryWrapper<DatasourceTableEntity>()
                        .eq(DatasourceTableEntity::getDatasourceId, datasourceId)
                        .eq(DatasourceTableEntity::getDeleted, 0));
        Map<String, DatasourceTableEntity> existingTableMap = existingTables.stream()
                .collect(Collectors.toMap(DatasourceTableEntity::getTableName, Function.identity(), (a, b) -> a));
        Map<String, DatasourceTableSaveItem> requestTableMap = items.stream()
                .filter(i -> i.getTableName() != null && !i.getTableName().isBlank())
                .collect(Collectors.toMap(DatasourceTableSaveItem::getTableName, Function.identity(), (a, b) -> a));

        // 修改人:祝奔；时间：2026-11-19T09:26:29.771Z；修改原因/内容：表不在启用列表时仅置表为不启用，不影响字段启用状态；代码来源：AI生成
        for (DatasourceTableEntity existing : existingTables) {
            if (!requestTableMap.containsKey(existing.getTableName())) {
                if (!Boolean.FALSE.equals(existing.getEnabled())) {
                    existing.setEnabled(false);
                    datasourceTableMapper.updateById(existing);
                }
            }
        }

        // 请求中的表：新增或更新，并按字段全量同步
        for (DatasourceTableSaveItem item : items) {
            if (item.getTableName() == null || item.getTableName().isBlank()) continue;
            DatasourceTableEntity table = existingTableMap.get(item.getTableName());
            if (table == null) {
                table = new DatasourceTableEntity();
                table.setDatasourceId(datasourceId);
                table.setTableName(item.getTableName());
                datasourceTableMapper.insert(table);
            }
            table.setTableComment(item.getTableComment());
            table.setTableSchema(item.getTableSchema());
            table.setTableType(item.getTableType());
            table.setEnabled(Boolean.TRUE.equals(item.getEnabled()));
            datasourceTableMapper.updateById(table);
            syncColumns(datasourceId, table.getId(), item);
        }
    }

    private void syncColumns(Long datasourceId, Long tableId, DatasourceTableSaveItem item) {
        List<DatasourceTableColEntity> existingCols = colMapper.selectList(
                new LambdaQueryWrapper<DatasourceTableColEntity>()
                        .eq(DatasourceTableColEntity::getDatasourceId, datasourceId)
                        .eq(DatasourceTableColEntity::getTableName, item.getTableName())
                        .eq(DatasourceTableColEntity::getDeleted, 0));
        Map<String, DatasourceTableColEntity> existingColMap = existingCols.stream()
                .collect(Collectors.toMap(DatasourceTableColEntity::getColumnName, Function.identity(), (a, b) -> a));

        List<DatasourceColumnSaveItem> colItems = item.getColumns() == null
                ? Collections.emptyList() : item.getColumns();
        Map<String, DatasourceColumnSaveItem> requestColMap = colItems.stream()
                .filter(c -> c.getColumnName() != null && !c.getColumnName().isBlank())
                .collect(Collectors.toMap(DatasourceColumnSaveItem::getColumnName, Function.identity(), (a, b) -> a));

        // 落库有但请求字段全量中不存在 → 软删除（对应数据库中已删除的字段）
        for (DatasourceTableColEntity col : existingCols) {
            if (!requestColMap.containsKey(col.getColumnName())) {
                col.setDeleted(1);
                colMapper.updateById(col);
            }
        }

        for (DatasourceColumnSaveItem colItem : colItems) {
            if (colItem.getColumnName() == null || colItem.getColumnName().isBlank()) continue;
            DatasourceTableColEntity col = existingColMap.get(colItem.getColumnName());
            if (col == null) {
                col = new DatasourceTableColEntity();
                col.setDatasourceId(datasourceId);
                col.setTableId(tableId);
                col.setTableName(item.getTableName());
                col.setColumnName(colItem.getColumnName());
                colMapper.insert(col);
            }
            col.setColumnType(colItem.getColumnType());
            col.setColumnComment(colItem.getColumnComment());
            col.setCustomComment(colItem.getCustomComment());
            col.setEnabled(Boolean.TRUE.equals(colItem.getEnabled()));
            colMapper.updateById(col);
        }
    }
}
