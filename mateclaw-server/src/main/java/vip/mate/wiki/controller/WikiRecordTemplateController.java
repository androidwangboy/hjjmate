package vip.mate.wiki.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vip.mate.common.result.R;
import vip.mate.wiki.dto.RecordTemplateSaveDTO;
import vip.mate.wiki.model.WikiRecordTemplateEntity;
import vip.mate.wiki.service.WikiRecordTemplateService;
import vip.mate.workspace.core.annotation.RequireWorkspaceRole;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 病历模板库接口（方案 B：独立维护，不挂 Wiki KB）。
 * <p>支持 Word / Excel / PDF 上传抽取样式，以及手动录入模板内容 JSON、扩展属性 JSON。
 *
 * <p>绑定语义：每个模板同时持有"样式"与"内容 JSON"与"扩展属性 JSON"，三者同表同行，
 * 召回时按样式命中即返回该行的 contentJson / extAttrsJson，不会错位。
 */
@Tag(name = "病历模板库（独立）")
@RestController
@RequestMapping("/api/v1/wiki/record-templates")
@RequiredArgsConstructor
public class WikiRecordTemplateController {

    private final WikiRecordTemplateService service;

    @RequireWorkspaceRole("member")
    @Operation(summary = "上传模板文件（Word/Excel/PDF）抽取样式，并可同时手动录入内容/扩展属性 JSON（样式不来自 content_json）")
    @PostMapping("/upload")
    public R<WikiRecordTemplateEntity> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(required = false) String contentJson,
                                              @RequestParam(required = false) String extAttrsJson,
                                              @RequestParam(required = false) Long kbId,
                                              @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        return R.ok(service.upload(file, contentJson, extAttrsJson, kbId, workspaceId));
    }

    @RequireWorkspaceRole("viewer")
    @Operation(summary = "分页列出模板（可按关键词/来源格式过滤）")
    @GetMapping
    public R<Map<String, Object>> list(@RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String sourceType,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        var result = service.list(workspaceId, keyword, sourceType, page, size);
        return R.ok(Map.of(
                "records", result.getRecords(),
                "total", result.getTotal(),
                "page", result.getCurrent(),
                "size", result.getSize()));
    }

    @RequireWorkspaceRole("viewer")
    @Operation(summary = "获取单个模板详情")
    @GetMapping("/{id}")
    public R<WikiRecordTemplateEntity> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequireWorkspaceRole("viewer")
    @Operation(summary = "预览模板源文件内容（抽取纯文本，docx/xlsx/pdf）")
    @GetMapping("/{id}/content")
    public R<String> fileContent(@PathVariable Long id) {
        return R.ok(service.extractFileContent(id));
    }

    @RequireWorkspaceRole("viewer")
    @Operation(summary = "获取模板源文件字节（下载 / PDF 内嵌预览）")
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        var blob = service.getFile(id);
        String disposition = "inline; filename=\"" + encodeFileName(blob.fileName()) + "\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, blob.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(blob.data());
    }

    private String encodeFileName(String name) {
        if (name == null || name.isBlank()) return "template";
        try {
            return URLEncoder.encode(name, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return name;
        }
    }

    @RequireWorkspaceRole("member")
    @Operation(summary = "更新模板（补/改 名称、内容 JSON、扩展属性 JSON）")
    @PutMapping("/{id}")
    public R<WikiRecordTemplateEntity> update(@PathVariable Long id,
                                             @RequestBody RecordTemplateSaveDTO dto) {
        return R.ok(service.update(id, dto.getName(), dto.getContentJson(), dto.getExtAttrsJson(), dto.getKbId()));
    }

    @RequireWorkspaceRole("member")
    @Operation(summary = "根据源文件重新抽取样式（仅上传型模板）")
    @PostMapping("/{id}/re-extract")
    public R<WikiRecordTemplateEntity> reExtract(@PathVariable Long id) {
        return R.ok(service.reExtract(id));
    }

    @RequireWorkspaceRole("admin")
    @Operation(summary = "删除模板（逻辑删除）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
