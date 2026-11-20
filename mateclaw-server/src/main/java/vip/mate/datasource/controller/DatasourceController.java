package vip.mate.datasource.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.datasource.model.DatasourceColumnVO;
import vip.mate.datasource.model.DatasourceEntity;
import vip.mate.datasource.model.DatasourceTableSaveRequest;
import vip.mate.datasource.model.DatasourceTableVO;
import vip.mate.datasource.service.DatasourceService;
import vip.mate.datasource.service.DatasourceTableColService;
import vip.mate.datasource.service.DatasourceTableService;

import java.util.List;
import java.util.Map;
import vip.mate.workspace.core.annotation.RequireGlobalAdmin;

/**
 * 数据源管理接口
 *
 * @author MateClaw Team
 */
@Tag(name = "数据源管理")
@RestController
@RequestMapping("/api/v1/datasources")
@RequiredArgsConstructor
public class DatasourceController {

    private final DatasourceService datasourceService;
    private final DatasourceTableService datasourceTableService;
    private final DatasourceTableColService datasourceTableColService;

    @Operation(summary = "获取数据源列表")
    @GetMapping
    //@RequireGlobalAdmin
    public R<List<DatasourceEntity>> list() {
        return R.ok(datasourceService.listAll());
    }

    @Operation(summary = "获取数据源详情")
    @GetMapping("/{id}")
    @RequireGlobalAdmin
    public R<DatasourceEntity> get(@PathVariable Long id) {
        return R.ok(datasourceService.getByIdMasked(id));
    }

    @Operation(summary = "创建数据源")
    @PostMapping
    @RequireGlobalAdmin
    public R<DatasourceEntity> create(@RequestBody DatasourceEntity entity) {
        return R.ok(datasourceService.create(entity));
    }

    @Operation(summary = "更新数据源")
    @PutMapping("/{id}")
    @RequireGlobalAdmin
    public R<DatasourceEntity> update(@PathVariable Long id, @RequestBody DatasourceEntity entity) {
        entity.setId(id);
        return R.ok(datasourceService.update(entity));
    }

    @Operation(summary = "删除数据源")
    @DeleteMapping("/{id}")
    @RequireGlobalAdmin
    public R<Void> delete(@PathVariable Long id) {
        datasourceService.delete(id);
        return R.ok();
    }

    @Operation(summary = "测试数据源连接")
    @PostMapping("/{id}/test")
    @RequireGlobalAdmin
    public R<Map<String, Object>> testConnection(@PathVariable Long id) {
        boolean ok = datasourceService.testConnection(id);
        return R.ok(Map.of("success", ok, "message", ok ? "连接成功" : "连接失败"));
    }

    // 修改人:祝奔；时间：2026-11-19T08:31:37.101Z；修改原因/内容：新增根据数据源ID查询表集合接口；代码来源：AI生成
    @Operation(summary = "查询数据源表集合")
    @GetMapping("/{id}/tables")
    @RequireGlobalAdmin
    public R<List<DatasourceTableVO>> tables(@PathVariable Long id) {
        return R.ok(datasourceTableService.listTables(id));
    }

    // 修改人:祝奔；时间：2026-11-19T09:02:06.584Z；修改原因/内容：新增根据表名查询字段集合接口；代码来源：AI生成
    @Operation(summary = "查询数据源表字段集合")
    @GetMapping("/{id}/tables/{tableName}/columns")
    @RequireGlobalAdmin
    public R<List<DatasourceColumnVO>> columns(@PathVariable Long id, @PathVariable String tableName) {
        return R.ok(datasourceTableColService.listColumns(id, tableName));
    }

    // 修改人:祝奔；时间：2026-11-19T09:18:14.021Z；修改原因/内容：新增保存表及字段配置接口；代码来源：AI生成
    @Operation(summary = "保存表及字段配置")
    @PostMapping("/tables/save")
    @RequireGlobalAdmin
    public R<Void> saveTables(@RequestBody DatasourceTableSaveRequest request) {
        datasourceTableService.saveTables(request);
        return R.ok();
    }

    @Operation(summary = "启用/禁用数据源")
    @PutMapping("/{id}/toggle")
    @RequireGlobalAdmin
    public R<DatasourceEntity> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        return R.ok(datasourceService.toggle(id, enabled));
    }

}
