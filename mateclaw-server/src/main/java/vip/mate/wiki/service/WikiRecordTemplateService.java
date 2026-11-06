package vip.mate.wiki.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vip.mate.exception.MateClawException;
import vip.mate.wiki.WikiProperties;
import vip.mate.wiki.model.WikiRecordTemplateEntity;
import vip.mate.wiki.repository.WikiRecordTemplateMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 病历模板库服务（方案 B：独立维护，支持 Excel / Word / PDF 上传 + 手动录入 JSON）。
 * <p>
 * 绑定语义：每一行模板同时持有"样式"(styleSignature/styleEmbedding/styleHash) 与
 * "内容"(contentJson) 与"扩展属性"(extAttrsJson)，召回时按样式命中即取该行内容，
 * 不会错位。styleHash 唯一约束保证同一种样式只存一份。
 * <p>
 * 约束：样式【只能】由上传的 docx/xlsx/pdf 文件抽取，<b>绝不</b>从 content_json 推导。
 * 手动录入仅用于 content_json / ext_attrs_json，且必须依附于一个已上传（有样式）的模板行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiRecordTemplateService {

    private final WikiRecordTemplateMapper mapper;
    private final WikiEmbeddingService embeddingService;
    private final WikiProperties properties;
    private final ObjectMapper objectMapper;
    private final RecordTemplateStyleExtractor extractor = new RecordTemplateStyleExtractor();

    private static final long DEFAULT_WORKSPACE = 1L;

    // ===================== 上传文件 → 抽取样式（草稿） =====================

    /**
     * 上传 Word/Excel/PDF 模板文件：抽取【样式】（结构）并落库。
     * <p>content_json / ext_attrs_json 为【可选】的手动录入项，与文件样式同表同行绑定；
     * 样式绝不来自 content_json（约束：样式只能由上传文件抽取）。
     */
    public WikiRecordTemplateEntity upload(MultipartFile file, String contentJson,
                                           String extAttrsJson, Long kbId, Long workspaceId) {
        if (file == null || file.isEmpty()) {
            throw new MateClawException(400, "上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        String sourceType = resolveSourceType(originalName);
        if (!List.of("docx", "xlsx", "pdf").contains(sourceType)) {
            throw new MateClawException(400, "仅支持 Word / Excel / PDF 模板文件（docx/xlsx/pdf）");
        }

        Path target = stageFile(originalName, file);
        TemplateStyle style = extractor.extract(target, sourceType);
        WikiRecordTemplateEntity entity = new WikiRecordTemplateEntity();
        entity.setName(stripExt(originalName));
        entity.setSourceType(sourceType);
        entity.setFileName(originalName);
        entity.setFilePath(target.toString());
        entity.setWorkspaceId(workspaceId == null ? DEFAULT_WORKSPACE : workspaceId);
        entity.setKbId(kbId);
        applyStyle(entity, style);

        // 手动录入：内容 JSON / 扩展属性 JSON（与文件样式同行绑定，不用于推导样式）
        boolean hasContent = false;
        if (contentJson != null && !contentJson.isBlank()) {
            validateJson("contentJson", contentJson);
            entity.setContentJson(pretty(contentJson));
            hasContent = true;
        }
        if (extAttrsJson != null && !extAttrsJson.isBlank()) {
            validateJson("extAttrsJson", extAttrsJson);
            entity.setExtAttrsJson(pretty(extAttrsJson));
        }
        entity.setStatus(hasContent ? "published" : "draft");
        mapper.insert(entity);
        log.info("[RecordTemplate] 上传并抽取样式: name={}, format={}, hash={}",
                entity.getName(), sourceType, entity.getStyleHash());
        return entity;
    }

    // ===================== 更新（补/改 名称、内容 JSON、扩展属性 JSON） =====================
    // 注意：更新内容绝不会反向推导样式；样式只来自上传文件（upload / reExtract）。

    public WikiRecordTemplateEntity update(Long id, String name, String contentJson,
                                           String extAttrsJson, Long kbId) {
        WikiRecordTemplateEntity entity = requireExisting(id);
        boolean changed = false;
        if (name != null && !name.isBlank() && !name.equals(entity.getName())) {
            entity.setName(name.trim());
            changed = true;
        }
        if (contentJson != null && !contentJson.isBlank()) {
            validateJson("contentJson", contentJson);
            entity.setContentJson(pretty(contentJson));
            changed = true;
        }
        if (extAttrsJson != null) {
            if (!extAttrsJson.isBlank()) validateJson("extAttrsJson", extAttrsJson);
            entity.setExtAttrsJson(extAttrsJson.isBlank() ? null : pretty(extAttrsJson));
            changed = true;
        }
        if (kbId != null) {
            entity.setKbId(kbId);
            changed = true;
        }
        if (changed) {
            entity.setStatus(entity.getContentJson() != null && !entity.getContentJson().isBlank()
                    ? "published" : "draft");
            entity.setUpdateTime(LocalDateTime.now());
            mapper.updateById(entity);
        }
        return entity;
    }

    // ===================== 重新抽取样式 =====================

    public WikiRecordTemplateEntity reExtract(Long id) {
        WikiRecordTemplateEntity entity = requireExisting(id);
        if (entity.getFilePath() == null) {
            throw new MateClawException(400, "该模板无源文件，无法重抽样式（仅上传型模板支持）");
        }
        TemplateStyle style = extractor.extract(Paths.get(entity.getFilePath()), entity.getSourceType());
        applyStyle(entity, style);
        entity.setUpdateTime(LocalDateTime.now());
        mapper.updateById(entity);
        return entity;
    }

    // ===================== 查询 =====================

    public IPage<WikiRecordTemplateEntity> list(Long workspaceId, String keyword,
                                               String sourceType, int page, int size) {
        LambdaQueryWrapper<WikiRecordTemplateEntity> q = new LambdaQueryWrapper<>();
        q.eq(WikiRecordTemplateEntity::getWorkspaceId, workspaceId == null ? DEFAULT_WORKSPACE : workspaceId)
          .eq(WikiRecordTemplateEntity::getDeleted, 0);
        if (sourceType != null && !sourceType.isBlank()) {
            q.eq(WikiRecordTemplateEntity::getSourceType, sourceType);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.like(WikiRecordTemplateEntity::getName, keyword);
        }
        q.orderByDesc(WikiRecordTemplateEntity::getCreateTime);
        return mapper.selectPage(new Page<>(Math.max(1, page), Math.max(1, size)), q);
    }

    public WikiRecordTemplateEntity get(Long id) {
        return requireExisting(id);
    }

    public void delete(Long id) {
        WikiRecordTemplateEntity entity = requireExisting(id);
        entity.setDeleted(1);
        entity.setUpdateTime(LocalDateTime.now());
        mapper.updateById(entity);
    }

    // ===================== 预览源文件内容 =====================

    /** 抽取源文件的纯文本内容（docx/xlsx/pdf），供前端预览 */
    public String extractFileContent(Long id) {
        WikiRecordTemplateEntity entity = requireExisting(id);
        if (entity.getFilePath() == null) {
            throw new MateClawException(400, "该模板无源文件，无法预览文件内容");
        }
        return extractor.extractText(Paths.get(entity.getFilePath()), entity.getSourceType());
    }

    /** 读取源文件字节，供前端下载 / 内嵌预览（PDF） */
    public FileBlob getFile(Long id) {
        WikiRecordTemplateEntity entity = requireExisting(id);
        if (entity.getFilePath() == null) {
            throw new MateClawException(400, "该模板无源文件");
        }
        Path path = Paths.get(entity.getFilePath()).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new MateClawException(404, "源文件不存在或已被清理");
        }
        try {
            byte[] data = Files.readAllBytes(path);
            return new FileBlob(data, entity.getFileName(), contentTypeOf(entity.getSourceType()));
        } catch (IOException e) {
            throw new MateClawException(500, "读取源文件失败: " + e.getMessage());
        }
    }

    /** 预览/下载用的文件载体 */
    public record FileBlob(byte[] data, String fileName, String contentType) {}

    private String contentTypeOf(String sourceType) {
        if (sourceType == null) return "application/octet-stream";
        return switch (sourceType.toLowerCase()) {
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    // ===================== 对话召回（样例模板样式比对） =====================

    /** 召回结果：命中实体 + 余弦相似度 + 原因（matched / no_templates / no_match / unsupported_format / embedding_unavailable） */
    public record TemplateMatch(WikiRecordTemplateEntity entity, float score, String reason) {}

    /** 样式余弦相似度命中阈值 */
    private static final float RECALL_THRESHOLD = 0.85f;

    /**
     * 对话召回：用上传的样例模板文件，与「当前 agent 绑定知识库」下的模板做样式余弦比对。
     * <ul>
     *   <li>kbIds == null：agent 未显式绑定 KB（wiki 走 workspace 级全量）→ 按 workspaceId 匹配；</li>
     *   <li>kbIds 为空集：wiki 禁用 → 无候选；</li>
     *   <li>kbIds 非空：按 kbId IN 集合过滤。</li>
     * </ul>
     * 命中阈值 {@link #RECALL_THRESHOLD}；不支持的格式 / 向量不可用 / 无候选 / 无命中均返回
     * entity==null（交由模型自由生成）。
     */
    public TemplateMatch matchBySample(Path sampleFile, String fileName, Set<Long> kbIds, Long workspaceId) {
        String sourceType = resolveSourceType(fileName);
        if (!List.of("docx", "xlsx", "pdf").contains(sourceType)) {
            return new TemplateMatch(null, 0f, "unsupported_format");
        }
        TemplateStyle style = extractor.extract(sampleFile, sourceType);
        WikiEmbeddingService.TemplateEmbedding emb = embeddingService.embedForTemplate(style.toStyleText());
        if (emb == null || emb.vector() == null) {
            return new TemplateMatch(null, 0f, "embedding_unavailable");
        }
        float[] sampleVec = emb.vector();
        String modelName = emb.modelName();

        List<WikiRecordTemplateEntity> candidates = listCandidates(kbIds, workspaceId);
        if (candidates.isEmpty()) {
            return new TemplateMatch(null, 0f, "no_templates");
        }

        WikiRecordTemplateEntity best = null;
        float bestScore = -1f;
        for (WikiRecordTemplateEntity c : candidates) {
            if (c.getStyleEmbedding() == null) continue;
            // 仅比较同嵌入模型产出的向量，跨模型余弦无意义
            if (c.getEmbeddingModel() == null || !c.getEmbeddingModel().equals(modelName)) continue;
            float[] cv = WikiEmbeddingService.bytesToFloats(c.getStyleEmbedding());
            float sim = WikiEmbeddingService.cosine(sampleVec, cv);
            if (sim > bestScore) {
                bestScore = sim;
                best = c;
            }
        }
        if (best == null || bestScore < RECALL_THRESHOLD) {
            return new TemplateMatch(null, bestScore, "no_match");
        }
        return new TemplateMatch(best, bestScore, "matched");
    }

    private List<WikiRecordTemplateEntity> listCandidates(Set<Long> kbIds, Long workspaceId) {
        if (kbIds != null && kbIds.isEmpty()) return List.of(); // wiki 禁用，无候选
        LambdaQueryWrapper<WikiRecordTemplateEntity> q = new LambdaQueryWrapper<>();
        q.eq(WikiRecordTemplateEntity::getDeleted, 0)
          .isNotNull(WikiRecordTemplateEntity::getStyleEmbedding);
        if (kbIds == null) {
            q.eq(WikiRecordTemplateEntity::getWorkspaceId, workspaceId == null ? DEFAULT_WORKSPACE : workspaceId);
        } else {
            q.in(WikiRecordTemplateEntity::getKbId, kbIds);
        }
        return mapper.selectList(q);
    }

    // ===================== 内部工具 =====================

    private WikiRecordTemplateEntity requireExisting(Long id) {
        WikiRecordTemplateEntity entity = mapper.selectById(id);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            throw new MateClawException(404, "模板不存在");
        }
        return entity;
    }

    /** 把抽取出的样式写进实体：签名 / 哈希 / 布局 / 计数 / 向量 */
    private void applyStyle(WikiRecordTemplateEntity entity, TemplateStyle style) {
        entity.setStyleSignature(style.toSignatureJson());
        entity.setStyleHash(style.sha256());
        entity.setLayoutType(style.getLayoutType());
        entity.setFieldCount(style.getFields() == null ? 0 : style.getFields().size());
        entity.setTableCount(style.getTables() == null ? 0 : style.getTables().size());
        entity.setSectionCount(style.getSections() == null ? 0 : style.getSections().size());

        WikiEmbeddingService.TemplateEmbedding emb = embeddingService.embedForTemplate(style.toStyleText());
        if (emb != null && emb.vector() != null) {
            entity.setStyleEmbedding(WikiEmbeddingService.floatsToBytes(emb.vector()));
            entity.setEmbeddingModel(emb.modelName());
            entity.setEmbeddingTextVersion(embeddingService.currentInputVersion());
        } else {
            log.warn("[RecordTemplate] 样式向量化不可用，模板 {} 将以 styleHash 精确匹配为主", entity.getName());
        }
    }

    private Path stageFile(String originalName, MultipartFile file) {
        try {
            Path uploadDir = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);
            String safe = originalName == null ? "template" : originalName.replaceAll("[^\\w.\\-一-龥]", "_");
            Path target = uploadDir.resolve(System.currentTimeMillis() + "_" + safe);
            file.transferTo(target);
            return target;
        } catch (IOException e) {
            throw new MateClawException(500, "文件暂存失败: " + e.getMessage());
        }
    }

    private String resolveSourceType(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "unknown";
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return switch (ext) {
            case "docx", "doc" -> "docx";
            case "xlsx", "xls" -> "xlsx";
            case "pdf" -> "pdf";
            default -> "unknown";
        };
    }

    private String stripExt(String fileName) {
        if (fileName == null) return "template";
        int i = fileName.lastIndexOf('.');
        return i > 0 ? fileName.substring(0, i) : fileName;
    }

    private void validateJson(String field, String json) {
        try {
            objectMapper.readTree(json);
        } catch (Exception e) {
            throw new MateClawException(400, field + " 不是合法 JSON: " + e.getMessage());
        }
    }

    private String pretty(String json) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json));
        } catch (Exception e) {
            return json;
        }
    }
}
