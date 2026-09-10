package vip.mate.portability.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vip.mate.audit.service.AuditEventService;
import vip.mate.common.result.R;
import vip.mate.exception.MateClawException;
import vip.mate.portability.model.BundleManifest;
import vip.mate.portability.service.PortabilityExportService;
import vip.mate.portability.service.PortabilityImportService;
import vip.mate.workspace.core.annotation.RequireWorkspaceRole;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Export / import of portable configuration assets (`.mcbundle`).
 *
 * <p>See {@code rfcs/portability-export-import.md}. The import side is
 * deliberately <b>append-only</b>: it never updates or deletes pre-existing
 * rows, has no overwrite mode, and stamps every created row with a batch id so
 * the whole import can be reverted.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/portability")
@RequiredArgsConstructor
@Tag(name = "可移植性（导出/导入）")
public class PortabilityController {

    private static final long MAX_UPLOAD_BYTES = 64L * 1024 * 1024;

    private final PortabilityExportService exportService;
    private final PortabilityImportService importService;
    private final AuditEventService auditEventService;

    @Operation(summary = "导出配置资产包（.mcbundle）")
    @GetMapping("/export")
    @RequireWorkspaceRole("admin")
    public ResponseEntity<byte[]> export(
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
            @RequestParam(required = false) String modules,
            @RequestParam(required = false) String agents,
            @RequestParam(name = "includeKnowledgeBase", defaultValue = "true") boolean includeKnowledgeBase,
            @RequestParam(name = "includePages", defaultValue = "false") boolean includePages,
            @RequestParam(name = "includeMemory", defaultValue = "true") boolean includeMemory,
            Authentication auth) throws IOException {
        long wsId = workspaceId != null ? workspaceId : 1L;
        Set<String> moduleSet = split(modules);
        Set<String> agentSet = split(agents);
        byte[] zip = exportService.exportBundle(wsId, moduleSet, agentSet, includeKnowledgeBase, includePages, includeMemory);
        auditEventService.record("EXPORT", "PORTABILITY_BUNDLE", null,
                "modules=" + moduleSet + ", agents=" + agentSet.size(), null);
        String filename = "assets-" + java.time.LocalDate.now() + ".mcbundle";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''"
                                + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8))
                .body(zip);
    }

    @Operation(summary = "预览导入（不写入任何数据）")
    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireWorkspaceRole("admin")
    public R<PortabilityImportService.Preview> preview(
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
            @RequestParam(required = false) String modules,
            @RequestPart("file") MultipartFile file) {
        long wsId = workspaceId != null ? workspaceId : 1L;
        PortabilityImportService.Bundle bundle = importService.parse(read(file));
        return R.ok(importService.preview(wsId, bundle, split(modules)));
    }

    @Operation(summary = "提交导入（仅新增，绝不覆盖现有数据）")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireWorkspaceRole("admin")
    public R<PortabilityImportService.ApplyResult> apply(
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
            @RequestParam(required = false) String modules,
            @RequestParam(defaultValue = "rename") String onConflict,
            @RequestPart("file") MultipartFile file,
            Authentication auth) {
        long wsId = workspaceId != null ? workspaceId : 1L;
        if (!Set.of("rename", "skip", "fail").contains(onConflict)) {
            throw new MateClawException("err.portability.bad_policy", "onConflict 只能是 rename / skip / fail");
        }
        PortabilityImportService.Bundle bundle = importService.parse(read(file));
        String operator = auth != null ? auth.getName() : "system";
        PortabilityImportService.ApplyResult result = importService.apply(wsId, bundle, split(modules), onConflict, operator);
        auditEventService.record("IMPORT", "PORTABILITY_BUNDLE", result.batchId(),
                "created=" + result.created() + ", skipped=" + result.skipped() + ", renamed=" + result.renamed(), null);
        return R.ok(result);
    }

    @Operation(summary = "撤销某次导入（只删除该批次新建的行）")
    @DeleteMapping("/import/{batchId}")
    @RequireWorkspaceRole("admin")
    public R<Map<String, Object>> revert(
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
            @PathVariable String batchId,
            Authentication auth) {
        long wsId = workspaceId != null ? workspaceId : 1L;
        int removed = importService.revert(wsId, batchId);
        auditEventService.record("REVERT", "PORTABILITY_BUNDLE", batchId, "removed=" + removed, null);
        return R.ok(Map.of("batchId", batchId, "removed", removed));
    }

    /** Current bundle format contract, so the UI can show it and validate early. */
    @Operation(summary = "查看导入包格式契约")
    @GetMapping("/format")
    @RequireWorkspaceRole("admin")
    public R<Map<String, Object>> format() {
        return R.ok(Map.of(
                "format", BundleManifest.FORMAT,
                "schemaVersion", BundleManifest.SCHEMA_VERSION,
                "modules", List.of("agents", "teams", "skills", "kbs", "workflows", "triggers", "memory"),
                "conflictPolicies", List.of("rename", "skip", "fail"),
                "renameSuffix", PortabilityImportService.RENAME_SUFFIX));
    }

    private static Set<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(csv.split(",")));
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MateClawException("err.portability.empty_file", "请选择要导入的 .mcbundle 文件");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new MateClawException("err.portability.file_too_large", "文件超过 64MB 上限");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new MateClawException("err.portability.unreadable", "无法读取上传文件: " + e.getMessage());
        }
    }
}
