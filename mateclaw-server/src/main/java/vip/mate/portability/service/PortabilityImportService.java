package vip.mate.portability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.agent.AgentService;
import vip.mate.agent.binding.service.AgentBindingService;
import vip.mate.agent.model.AgentEntity;
import vip.mate.agent.repository.AgentMapper;
import vip.mate.exception.MateClawException;
import vip.mate.portability.model.BundleManifest;
import vip.mate.skill.model.SkillEntity;
import vip.mate.skill.repository.SkillMapper;
import vip.mate.skill.service.SkillFileService;
import vip.mate.skill.service.SkillService;
import vip.mate.team.model.AgentTeamEntity;
import vip.mate.team.model.AgentTeamMemberEntity;
import vip.mate.team.repository.AgentTeamMapper;
import vip.mate.team.repository.AgentTeamMemberMapper;
import vip.mate.team.service.TeamService;
import vip.mate.trigger.model.TriggerEntity;
import vip.mate.trigger.repository.TriggerMapper;
import vip.mate.trigger.service.TriggerService;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;
import vip.mate.wiki.service.WikiKnowledgeBaseService;
import vip.mate.wiki.service.WikiRawMaterialService;
import vip.mate.workflow.compiler.PublishContext;
import vip.mate.workflow.compiler.WorkflowAclPort;
import vip.mate.workflow.compiler.WorkflowCompiler;
import vip.mate.workflow.model.WorkflowEntity;
import vip.mate.workflow.service.WorkflowService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Applies a {@code .mcbundle} to the target workspace under a strict
 * <b>append-only</b> contract:
 *
 * <ol>
 *   <li>Existing rows are never updated and never deleted — import only inserts.</li>
 *   <li>There is no "overwrite" mode; a name clash resolves to {@code skip}
 *       (identical content) or {@code rename} (different content), never a
 *       mutation of what is already there.</li>
 *   <li>Every inserted row is stamped with a batch id so the whole import can
 *       be reverted without touching anything else.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortabilityImportService {

    /** Suffix applied when a name clash resolves to {@code rename}. */
    public static final String RENAME_SUFFIX = " (imported)";

    /** Memory files accepted from a bundle — mirrors the exporter's whitelist. */
    private static final Set<String> MEMORY_FILE_WHITELIST =
            Set.of("AGENTS.md", "MEMORY.md", "PROFILE.md", "KNOWLEDGE.md");

    private static final long MAX_UNCOMPRESSED_BYTES = 64L * 1024 * 1024;
    private static final int MAX_ENTRIES = 500;
    private static final int MAX_ENTRY_BYTES = 16 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final PortabilityFingerprint fingerprint;
    private final AgentService agentService;
    private final AgentMapper agentMapper;
    private final AgentBindingService bindingService;
    private final SkillService skillService;
    private final SkillMapper skillMapper;
    private final SkillFileService skillFileService;
    private final WikiKnowledgeBaseService kbService;
    private final vip.mate.wiki.repository.WikiKnowledgeBaseMapper kbMapper;
    private final WikiRawMaterialService rawService;
    private final vip.mate.wiki.service.WikiPageService wikiPageService;
    private final vip.mate.workflow.repository.WorkflowRevisionMapper workflowRevisionMapper;
    private final vip.mate.workspace.document.WorkspaceFileService workspaceFileService;
    private final WorkflowService workflowService;
    private final WorkflowCompiler workflowCompiler;
    private final WorkflowAclPort workflowAclPort;
    private final TriggerService triggerService;
    private final TriggerMapper triggerMapper;
    private final TeamService teamService;
    private final AgentTeamMapper teamMapper;
    private final AgentTeamMemberMapper memberMapper;

    // ===================== bundle reading =====================

    /** Parsed bundle held in memory between the preview and the apply call. */
    public record Bundle(BundleManifest manifest, Map<String, String> files) {

        public String json(String path) {
            return files.get(path);
        }
    }

    public Bundle parse(byte[] payload) {
        Map<String, String> files = new LinkedHashMap<>();
        long total = 0;
        int entries = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(payload))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (++entries > MAX_ENTRIES) {
                    throw new MateClawException("err.portability.too_many_entries", "导入包条目过多");
                }
                String name = sanitize(entry.getName());
                byte[] buf = zis.readNBytes(MAX_ENTRY_BYTES + 1);
                if (buf.length > MAX_ENTRY_BYTES) {
                    throw new MateClawException("err.portability.entry_too_large", "导入包内单个文件过大: " + name);
                }
                total += buf.length;
                if (total > MAX_UNCOMPRESSED_BYTES) {
                    throw new MateClawException("err.portability.bundle_too_large", "导入包解压后体积过大");
                }
                files.put(name, new String(buf, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new MateClawException("err.portability.unreadable", "无法读取导入包: " + e.getMessage());
        }
        String manifestJson = files.get("manifest.json");
        if (manifestJson == null) {
            throw new MateClawException("err.portability.no_manifest", "导入包缺少 manifest.json");
        }
        BundleManifest manifest;
        try {
            manifest = objectMapper.readValue(manifestJson, BundleManifest.class);
        } catch (IOException e) {
            throw new MateClawException("err.portability.bad_manifest", "manifest.json 无法解析");
        }
        if (!BundleManifest.FORMAT.equals(manifest.getFormat())) {
            throw new MateClawException("err.portability.bad_format", "不是有效的 mcbundle 包");
        }
        if (!BundleManifest.SCHEMA_VERSION.equals(manifest.getSchemaVersion())) {
            throw new MateClawException("err.portability.version_mismatch",
                    "包格式版本不兼容：包为 " + manifest.getSchemaVersion() + "，本环境支持 " + BundleManifest.SCHEMA_VERSION);
        }
        // Integrity: every declared payload must match its checksum.
        manifest.getChecksums().forEach((path, expected) -> {
            String body = files.get(path);
            if (body == null) {
                throw new MateClawException("err.portability.missing_file", "导入包缺少文件: " + path);
            }
            String actual = "sha256:" + sha256(body);
            if (!actual.equals(expected)) {
                throw new MateClawException("err.portability.checksum_mismatch", "文件校验失败: " + path);
            }
        });
        return new Bundle(manifest, files);
    }

    /** Rejects zip-slip style paths and keeps the tree flat-shallow. */
    private static String sanitize(String raw) {
        String name = raw.replace('\\', '/');
        while (name.contains("//")) {
            name = name.replace("//", "/");
        }
        if (name.contains("..") || name.startsWith("/")) {
            throw new MateClawException("err.portability.unsafe_path", "导入包含非法路径: " + raw);
        }
        return name;
    }

    // ===================== preview =====================

    public record Decision(String module, String name, String action, String detail) {
    }

    public record Preview(BundleManifest manifest, List<Decision> items, List<String> warnings) {
    }

    /**
     * Dry run. Classifies every entity as {@code create}, {@code skip}
     * (identical content already present) or {@code rename} (same name,
     * different content — imported under a suffix so nothing is overwritten).
     */
    public Preview preview(Long workspaceId, Bundle bundle, Set<String> modules) {
        List<Decision> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>(bundle.manifest().getWarnings());

        Map<String, String> agentHashes = existingAgentHashes(workspaceId);
        Map<String, String> skillHashes = existingSkillHashes(workspaceId);
        // Only hash pages when the bundle actually carries them — otherwise a
        // pages-less bundle would never match an existing KB and every
        // re-import would degrade into a rename.
        boolean bundleHasPages = bundleCarriesPages(bundle);
        Map<String, String> kbHashes = existingKbHashes(workspaceId, bundleHasPages);
        Map<String, String> wfHashes = existingWorkflowHashes(workspaceId);
        Map<String, String> triggerHashes = existingTriggerHashes(workspaceId);
        Map<String, String> teamHashes = existingTeamHashes(workspaceId);

        if (wants(modules, "skills")) {
            for (JsonNode n : array(bundle, "skills.json")) {
                classify(items, "skills", n, skillHashes);
            }
        }
        if (wants(modules, "kbs")) {
            for (JsonNode n : array(bundle, "knowledge-bases.json")) {
                classify(items, "kbs", n, kbHashes);
            }
        }
        if (wants(modules, "agents")) {
            for (JsonNode n : array(bundle, "agents.json")) {
                classify(items, "agents", n, agentHashes);
                // Dependency availability: references must exist or be importable.
                for (JsonNode ref : n.path("skills")) {
                    String refName = ref.asText();
                    if (!skillHashes.containsKey(refName) && !inBundle(bundle, "skills.json", refName)) {
                        warnings.add("专家「" + n.path("name").asText() + "」引用了包内与环境中都不存在的技能：" + refName);
                    }
                }
                for (JsonNode ref : n.path("knowledgeBases")) {
                    String refName = ref.asText();
                    if (!kbHashes.containsKey(refName) && !inBundle(bundle, "knowledge-bases.json", refName)) {
                        warnings.add("专家「" + n.path("name").asText() + "」引用了不存在的知识库：" + refName);
                    }
                }
            }
        }
        if (wants(modules, "teams")) {
            for (JsonNode n : array(bundle, "teams.json")) {
                classify(items, "teams", n, teamHashes);
            }
        }
        if (wants(modules, "workflows")) {
            for (JsonNode n : array(bundle, "workflows.json")) {
                // Workflows hash through the graph-aware builder (not the raw
                // map) so formatting and employeeName hints are normalized away.
                classify(items, "workflows", n, wfHashes, workflowHash(n));
            }
        }
        if (wants(modules, "triggers")) {
            for (JsonNode n : array(bundle, "triggers.json")) {
                classify(items, "triggers", n, triggerHashes);
            }
        }
        if (wants(modules, "memory")) {
            for (JsonNode n : array(bundle, "agent-memory.json")) {
                String agentName = n.path("agent").asText();
                int files = n.path("files").size();
                // Memory is only written for experts this import creates. If the
                // owning expert already exists and is being skipped, its memory
                // is untouched — say so instead of promising a write.
                boolean ownerSkipped = agentHashes.containsKey(agentName);
                items.add(new Decision("memory", agentName, ownerSkipped ? "skip" : "create",
                        ownerSkipped
                                ? "归属专家在目标环境已存在且未被改动，其记忆文件保持不变（" + files + " 个文件不写入）"
                                : "写入记忆文件 " + files + " 个（仅写入本包新建的专家）"));
            }
        }
        return new Preview(bundle.manifest(), items, warnings);
    }

    private void classify(List<Decision> items, String module, JsonNode node, Map<String, String> existing) {
        classify(items, module, node, existing, fingerprint.hash(toMap(node)));
    }

    private void classify(List<Decision> items, String module, JsonNode node, Map<String, String> existing,
                          String hash) {
        String name = node.path("name").asText();
        // Never trust the bundled contentHash: recompute it from the payload we
        // actually received. A hand-edited (or tampered) bundle would otherwise
        // claim to be identical to the target and be silently skipped.
        String current = existing.get(name);
        if (current == null) {
            items.add(new Decision(module, name, "create", "将新建"));
        } else if (current.equals(hash)) {
            items.add(new Decision(module, name, "skip", "目标环境已存在且内容一致，跳过（不改动任何现有数据）"));
        } else {
            items.add(new Decision(module, name, "rename",
                    "目标环境已存在同名但内容不同 → 以「" + name + RENAME_SUFFIX + "」新建，现有数据保持不变"));
        }
    }

    // ===================== apply =====================

    public record ApplyResult(String batchId, int created, int skipped, int renamed, List<String> warnings) {
    }

    /**
     * Commits the import. Single transaction: any failure rolls the whole
     * bundle back so a half-imported workspace is impossible.
     */
    @Transactional
    public ApplyResult apply(Long workspaceId, Bundle bundle, Set<String> modules, String onConflict, String operator) {
        String batchId = UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> nameMap = new LinkedHashMap<>(); // bundle name -> target name
        Set<String> createdNames = new java.util.HashSet<>(); // names taken by this batch
        int created = 0;
        int skipped = 0;
        int renamed = 0;
        List<String> warnings = new ArrayList<>(bundle.manifest().getWarnings());

        Map<String, String> agentHashes = existingAgentHashes(workspaceId);
        Map<String, String> skillHashes = existingSkillHashes(workspaceId);
        boolean bundleHasPages = bundleCarriesPages(bundle);
        Map<String, String> kbHashes = existingKbHashes(workspaceId, bundleHasPages);
        Map<String, String> wfHashes = existingWorkflowHashes(workspaceId);
        Map<String, String> triggerHashes = existingTriggerHashes(workspaceId);
        Map<String, String> teamHashes = existingTeamHashes(workspaceId);

        // 1. Skills --------------------------------------------------------
        if (wants(modules, "skills")) {
            for (JsonNode n : array(bundle, "skills.json")) {
                String name = n.path("name").asText();
                String target = resolveName(name, fingerprint.hash(toMap(n)), skillHashes, onConflict, createdNames);
                if (target == null) {
                    skipped++;
                    continue;
                }
                if (!target.equals(name)) {
                    renamed++;
                }
                SkillEntity entity = new SkillEntity();
                entity.setName(target);
                entity.setNameZh(text(n, "nameZh"));
                entity.setNameEn(text(n, "nameEn"));
                entity.setDescription(text(n, "description"));
                entity.setIcon(text(n, "icon"));
                entity.setVersion(text(n, "version"));
                entity.setAuthor(text(n, "author"));
                entity.setTags(text(n, "tags"));
                entity.setSkillContent(text(n, "skillContent"));
                entity.setSkillType("custom");
                entity.setEnabled(true);
                entity.setWorkspaceId(workspaceId);
                entity.setImportBatch(batchId);
                SkillEntity saved = skillService.createSkill(entity);
                Map<String, String> files = new LinkedHashMap<>();
                n.path("files").fields().forEachRemaining(e -> files.put(e.getKey(), e.getValue().asText()));
                if (!files.isEmpty()) {
                    skillFileService.applyBundleFiles(saved.getId(), files, true);
                }
                nameMap.put(name, target);
                createdNames.add(target);
                created++;
            }
        }

        // 2. Knowledge bases ----------------------------------------------
        if (wants(modules, "kbs")) {
            for (JsonNode n : array(bundle, "knowledge-bases.json")) {
                String name = n.path("name").asText();
                String target = resolveName(name, fingerprint.hash(toMap(n)), kbHashes, onConflict, createdNames);
                if (target == null) {
                    skipped++;
                    continue;
                }
                if (!target.equals(name)) {
                    renamed++;
                }
                WikiKnowledgeBaseEntity kb = kbService.create(target, text(n, "description"), null, workspaceId);
                stampKbBatch(kb.getId(), batchId);
                if (n.hasNonNull("configContent")) {
                    kbService.updateConfig(kb.getId(), n.get("configContent").asText());
                }
                // Register the resolved name BEFORE restoring children: the
                // page-restore guard below keys off nameMap to prove this KB
                // was created by this import (never a reused, pre-existing one).
                nameMap.put(name, target);
                for (JsonNode raw : n.path("raws")) {
                    rawService.addText(kb.getId(), raw.path("title").asText(), raw.path("content").asText());
                }
                int pagesWritten = 0;
                for (JsonNode page : n.path("pages")) {
                    String slug = page.path("slug").asText("");
                    if (slug.isBlank()) {
                        continue;
                    }
                    // Pages only ever land in a KB this import just created —
                    // writing into a reused (pre-existing) KB would mutate data
                    // that was already there.
                    if (!nameMap.containsKey(name)) {
                        continue;
                    }
                    try {
                        wikiPageService.createPage(kb.getId(), slug, page.path("title").asText(slug),
                                page.path("content").asText(""), text(page, "summary"), null, text(page, "pageType"));
                        pagesWritten++;
                    } catch (RuntimeException e) {
                        warnings.add("知识库「" + target + "」页面「" + slug + "」写入失败：" + e.getMessage());
                    }
                }
                if (pagesWritten > 0) {
                    log.info("[portability] restored {} wiki pages into KB {}", pagesWritten, target);
                }
                createdNames.add(target);
                created++;
            }
        }

        // 3. Experts -------------------------------------------------------
        if (wants(modules, "agents")) {
            for (JsonNode n : array(bundle, "agents.json")) {
                String name = n.path("name").asText();
                String target = resolveName(name, fingerprint.hash(toMap(n)), agentHashes, onConflict, createdNames);
                if (target == null) {
                    skipped++;
                    continue;
                }
                if (!target.equals(name)) {
                    renamed++;
                }
                AgentEntity agent = new AgentEntity();
                agent.setName(target);
                agent.setDescription(text(n, "description"));
                agent.setAgentType(n.hasNonNull("agentType") ? n.get("agentType").asText() : "react");
                agent.setRuntimeType(text(n, "runtimeType"));
                agent.setSystemPrompt(text(n, "systemPrompt"));
                agent.setIcon(text(n, "icon"));
                agent.setTags(text(n, "tags"));
                agent.setMaxIterations(n.hasNonNull("maxIterations") ? n.get("maxIterations").asInt() : 10);
                agent.setEnabled(n.path("enabled").asBoolean(true));
                agent.setDefaultThinkingLevel(text(n, "defaultThinkingLevel"));
                agent.setModelName(text(n, "modelName"));
                agent.setWorkspaceId(workspaceId);
                agent.setImportBatch(batchId);
                AgentEntity saved = agentService.createAgent(agent);

                List<Long> skillIds = new ArrayList<>();
                for (JsonNode ref : n.path("skills")) {
                    String resolved = nameMap.getOrDefault(ref.asText(), ref.asText());
                    SkillEntity s = findSkillByName(workspaceId, resolved);
                    if (s != null) {
                        skillIds.add(s.getId());
                    } else {
                        warnings.add("专家「" + target + "」的技能引用未解析：" + ref.asText());
                    }
                }
                if (!skillIds.isEmpty()) {
                    bindingService.setSkillBindings(saved.getId(), skillIds);
                }
                List<Long> kbIds = new ArrayList<>();
                for (JsonNode ref : n.path("knowledgeBases")) {
                    String resolved = nameMap.getOrDefault(ref.asText(), ref.asText());
                    WikiKnowledgeBaseEntity kb = findKbByName(workspaceId, resolved);
                    if (kb != null) {
                        kbIds.add(kb.getId());
                    } else {
                        warnings.add("专家「" + target + "」的知识库引用未解析：" + ref.asText());
                    }
                }
                if (!kbIds.isEmpty()) {
                    bindingService.setKbBindings(saved.getId(), kbIds);
                }
                List<String> tools = new ArrayList<>();
                for (JsonNode ref : n.path("tools")) {
                    tools.add(ref.asText());
                }
                if (!tools.isEmpty()) {
                    try {
                        bindingService.setToolBindings(saved.getId(), tools);
                    } catch (RuntimeException e) {
                        warnings.add("专家「" + target + "」的部分工具未解析（目标环境无同名工具）");
                    }
                }
                nameMap.put(name, target);
                createdNames.add(target);
                created++;
            }
        }

        // 4. Teams ---------------------------------------------------------
        if (wants(modules, "teams")) {
            for (JsonNode n : array(bundle, "teams.json")) {
                String name = n.path("name").asText();
                String target = resolveName(name, fingerprint.hash(toMap(n)), teamHashes, onConflict, createdNames);
                if (target == null) {
                    skipped++;
                    continue;
                }
                if (!target.equals(name)) {
                    renamed++;
                }
                String leadName = nameMap.getOrDefault(n.path("lead").asText(), n.path("lead").asText());
                AgentEntity lead = findAgentByName(workspaceId, leadName);
                if (lead == null) {
                    warnings.add("团队「" + target + "」的 Lead 未解析：" + leadName + "，已跳过该团队");
                    continue;
                }
                if (alreadyInTeam(lead.getId())) {
                    warnings.add("团队「" + target + "」的 Lead「" + leadName + "」已属于其它团队，已跳过该团队");
                    continue;
                }
                List<Long> memberIds = new ArrayList<>();
                for (JsonNode m : n.path("members")) {
                    String memberName = nameMap.getOrDefault(m.path("name").asText(), m.path("name").asText());
                    AgentEntity member = findAgentByName(workspaceId, memberName);
                    if (member == null) {
                        warnings.add("团队「" + target + "」成员未解析：" + memberName);
                        continue;
                    }
                    if (alreadyInTeam(member.getId())) {
                        warnings.add("团队「" + target + "」成员「" + memberName + "」已属于其它团队，已跳过该成员");
                        continue;
                    }
                    memberIds.add(member.getId());
                }
                AgentTeamEntity team = teamService.createTeam(workspaceId, target, text(n, "description"),
                        lead.getId(), memberIds, operator);
                stampTeamBatch(team.getId(), batchId);
                createdNames.add(target);
                created++;
            }
        }

        // 5. Workflows -----------------------------------------------------
        if (wants(modules, "workflows")) {
            for (JsonNode n : array(bundle, "workflows.json")) {
                String name = n.path("name").asText();
                String target = resolveName(name, workflowHash(n), wfHashes, onConflict, createdNames);
                if (target == null) {
                    skipped++;
                    continue;
                }
                if (!target.equals(name)) {
                    renamed++;
                }
                String graph = n.path("graphJson").asText();
                // Re-point every agentName through the resolved name map so a
                // renamed expert stays reachable from the imported graph.
                graph = rewriteAgentNames(graph, nameMap);
                // P1: turn the portable employeeName back into this
                // environment's employeeId. A literal dev-environment ID is
                // meaningless here and would fail the compiler's ACL check.
                graph = resolveWriteMemoryEmployee(graph, workspaceId, nameMap, target);
                WorkflowEntity wf = new WorkflowEntity();
                wf.setName(target);
                wf.setDescription(text(n, "description"));
                wf.setEnabled(n.path("enabled").asBoolean(true));
                wf.setWorkspaceId(workspaceId);
                wf.setImportBatch(batchId);
                WorkflowEntity saved = workflowService.create(wf);
                workflowService.saveDraft(saved.getId(), workspaceId, graph, null);
                // Compile gate: an unpublishable workflow must not be imported.
                WorkflowCompiler.Result result = workflowCompiler.compile(graph,
                        new PublishContext(workspaceId, 0L), workflowAclPort);
                if (!result.ok()) {
                    throw new MateClawException("err.portability.workflow_compile_failed",
                            "工作流「" + target + "」编译未通过，整包已回滚：" + result.errors());
                }
                workflowService.publish(saved.getId(), workspaceId, null, "imported from mcbundle " + batchId);
                createdNames.add(target);
                created++;
            }
        }

        // 6. Triggers ------------------------------------------------------
        if (wants(modules, "triggers")) {
            for (JsonNode n : array(bundle, "triggers.json")) {
                String name = n.path("name").asText();
                String target = resolveName(name, fingerprint.hash(toMap(n)), triggerHashes, onConflict, createdNames);
                if (target == null) {
                    skipped++;
                    continue;
                }
                if (!target.equals(name)) {
                    renamed++;
                }
                WorkflowEntity wf = workflowService.listByWorkspace(workspaceId).stream()
                        .filter(w -> w.getName().equals(n.path("targetWorkflow").asText()))
                        .findFirst().orElse(null);
                if (wf == null) {
                    warnings.add("触发器「" + target + "」的目标工作流未解析：" + n.path("targetWorkflow").asText());
                    continue;
                }
                TriggerEntity trigger = new TriggerEntity();
                trigger.setName(target);
                trigger.setPatternType(n.path("patternType").asText());
                trigger.setPatternJson(text(n, "patternJson"));
                trigger.setTargetType(n.path("targetType").asText());
                trigger.setTargetId(wf.getId());
                trigger.setPayloadTemplate(text(n, "payloadTemplate"));
                trigger.setRateLimitPerMin(n.path("rateLimitPerMin").asInt(10));
                trigger.setDedupWindowSecs(n.path("dedupWindowSecs").asInt(60));
                trigger.setBotSelfFilter(n.path("botSelfFilter").asBoolean(true));
                trigger.setEnabled(n.path("enabled").asBoolean(true));
                trigger.setWorkspaceId(workspaceId);
                trigger.setImportBatch(batchId);
                triggerService.create(trigger, workspaceId);
                createdNames.add(target);
                created++;
            }
        }

        // 7. Expert memory files — only for experts this import created or
        //    renamed. A skipped (pre-existing) expert keeps its own memory.
        if (wants(modules, "memory")) {
            for (JsonNode n : array(bundle, "agent-memory.json")) {
                String agentName = n.path("agent").asText();
                String resolved = nameMap.get(agentName);
                if (resolved == null) {
                    continue; // skipped: never touch that expert's memory
                }
                AgentEntity agent = findAgentByName(workspaceId, resolved);
                if (agent == null) {
                    warnings.add("记忆文件归属专家未解析：" + resolved);
                    continue;
                }
                int written = 0;
                var files = n.path("files");
                var it = files.fields();
                while (it.hasNext()) {
                    var e = it.next();
                    String filename = e.getKey();
                    if (!MEMORY_FILE_WHITELIST.contains(filename)) {
                        continue;
                    }
                    try {
                        workspaceFileService.saveFile(agent.getId(), filename, e.getValue().asText());
                        written++;
                    } catch (RuntimeException ex) {
                        warnings.add("专家「" + resolved + "」记忆文件「" + filename + "」写入失败：" + ex.getMessage());
                    }
                }
                created += written > 0 ? 0 : 0; // files are not counted as entities
                if (written > 0) {
                    log.info("[portability] wrote {} memory files for expert {}", written, resolved);
                }
            }
        }

        log.info("[portability] applied batch {} to workspace {}: created={} skipped={} renamed={}",
                batchId, workspaceId, created, skipped, renamed);
        return new ApplyResult(batchId, created, skipped, renamed, warnings);
    }

    // ===================== revert =====================

    /**
     * Undoes exactly one import: deletes only the rows created by that batch.
     * Rows that existed before the import are never touched.
     */
    @Transactional
    public int revert(Long workspaceId, String batchId) {
        int removed = 0;
        // Children first: members, then teams.
        List<Long> teamIds = teamMapper.selectList(new LambdaQueryWrapper<AgentTeamEntity>()
                        .eq(AgentTeamEntity::getImportBatch, batchId)
                        .eq(AgentTeamEntity::getWorkspaceId, workspaceId))
                .stream().map(AgentTeamEntity::getId).toList();
        for (Long teamId : teamIds) {
            removed += memberMapper.delete(new LambdaQueryWrapper<AgentTeamMemberEntity>()
                    .eq(AgentTeamMemberEntity::getTeamId, teamId));
        }
        removed += triggerMapper.delete(new LambdaQueryWrapper<TriggerEntity>()
                .eq(TriggerEntity::getImportBatch, batchId)
                .eq(TriggerEntity::getWorkspaceId, workspaceId));
        removed += deleteWorkflowsByBatch(workspaceId, batchId);
        removed += deleteAgentsByBatch(workspaceId, batchId);
        removed += deleteSkillsByBatch(workspaceId, batchId);
        removed += deleteKbsByBatch(workspaceId, batchId);
        removed += deleteTeamsByBatch(workspaceId, batchId);
        log.info("[portability] reverted batch {} in workspace {}: {} rows removed", batchId, workspaceId, removed);
        return removed;
    }

    // ===================== helpers =====================

    /**
     * @return the name to create under, or {@code null} when the entity should
     *         be skipped because an identical copy already exists.
     */
    private String resolveName(String name, String hash, Map<String, String> existing, String onConflict,
                               Set<String> createdNames) {
        String current = existing.get(name);
        if (current == null) {
            return name;
        }
        if (current.equals(hash)) {
            return null; // identical — non-destructive skip
        }
        if ("skip".equalsIgnoreCase(onConflict)) {
            return null;
        }
        if ("fail".equalsIgnoreCase(onConflict)) {
            throw new MateClawException("err.portability.conflict",
                    "目标环境已存在同名但内容不同的资产：" + name + "（策略=fail，整包未导入）");
        }
        // Default rename — never overwrite. The suffixed name can itself be
        // taken (a second import of the same bundle), so keep numbering until
        // the candidate is free instead of dying on a unique-index violation.
        String candidate = name + RENAME_SUFFIX;
        int seq = 2;
        while (existing.containsKey(candidate) || createdNames.contains(candidate)) {
            candidate = name + " (imported " + seq + ")";
            seq++;
        }
        return candidate;
    }

    /** Graph-aware fingerprint for a workflow node. */
    private String workflowHash(JsonNode node) {
        return fingerprint.hash(fingerprint.workflow(node.path("name").asText(),
                text(node, "description"), node.path("enabled").asBoolean(true),
                node.path("graphJson").asText("")));
    }

    /** JsonNode → ordered map, so the fingerprint sees the same shape as export. */
    private Map<String, Object> toMap(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            if (v.isObject()) {
                map.put(e.getKey(), toMap(v));
            } else if (v.isArray()) {
                List<Object> list = new ArrayList<>();
                for (JsonNode child : v) {
                    list.add(child.isObject() ? toMap(child) : child.isNumber() ? child.numberValue()
                            : child.isBoolean() ? child.booleanValue() : child.asText());
                }
                map.put(e.getKey(), list);
            } else if (v.isNumber()) {
                map.put(e.getKey(), v.numberValue());
            } else if (v.isBoolean()) {
                map.put(e.getKey(), v.booleanValue());
            } else if (v.isNull()) {
                map.put(e.getKey(), null);
            } else {
                map.put(e.getKey(), v.asText());
            }
        });
        return map;
    }

    private static boolean wants(Set<String> modules, String module) {
        return modules == null || modules.isEmpty() || modules.contains(module);
    }

    private JsonNode array(Bundle bundle, String path) {
        String body = bundle.json(path);
        if (body == null) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            return node.isArray() ? node : objectMapper.createArrayNode();
        } catch (IOException e) {
            throw new MateClawException("err.portability.bad_payload", "包内文件无法解析: " + path);
        }
    }

    private static boolean inBundle(Bundle bundle, String path, String name) {
        String body = bundle.json(path);
        return body != null && body.contains("\"name\" : \"" + name + "\"") || (body != null && body.contains("\"name\":\"" + name + "\""));
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    /**
     * Resolves {@code mode.employeeName} (portable) back into a local
     * {@code mode.employeeId}. Blanks the id when the name can't be resolved so
     * the compiler reports a clear "write_memory requires employeeId" error
     * against this step rather than a confusing cross-workspace ACL failure.
     */
    private String resolveWriteMemoryEmployee(String graphJson, Long workspaceId,
                                              Map<String, String> nameMap, String workflowName) {
        try {
            JsonNode root = objectMapper.readTree(graphJson);
            JsonNode steps = root.path("steps");
            if (!steps.isArray()) {
                return graphJson;
            }
            for (JsonNode step : steps) {
                JsonNode mode = step.path("mode");
                if (!"write_memory".equals(mode.path("type").asText())) {
                    continue;
                }
                String employeeName = mode.path("employeeName").asText("");
                if (employeeName.isBlank()) {
                    continue;
                }
                String resolved = nameMap.getOrDefault(employeeName, employeeName);
                AgentEntity agent = findAgentByName(workspaceId, resolved);
                if (agent == null) {
                    throw new MateClawException("err.portability.employee_unresolved",
                            "工作流「" + workflowName + "」的 write_memory 步骤引用了目标环境不存在的专家："
                                    + employeeName + "（请先在目标环境创建该专家，或从包中移除该步骤）");
                }
                ((com.fasterxml.jackson.databind.node.ObjectNode) mode).put("employeeId", String.valueOf(agent.getId()));
            }
            return objectMapper.writeValueAsString(root);
        } catch (MateClawException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[portability] could not resolve write_memory employeeName: {}", e.getMessage());
            return graphJson;
        }
    }

    /** Rewrites {@code agentName} values inside a workflow graph through the resolved name map. */
    private String rewriteAgentNames(String graphJson, Map<String, String> nameMap) {
        if (nameMap == null || nameMap.isEmpty()) {
            return graphJson;
        }
        try {
            JsonNode root = objectMapper.readTree(graphJson);
            walk(root, nameMap);
            return objectMapper.writeValueAsString(root);
        } catch (IOException e) {
            log.warn("[portability] could not rewrite agent names in workflow graph: {}", e.getMessage());
            return graphJson;
        }
    }

    private void walk(JsonNode node, Map<String, String> nameMap) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            JsonNode agentName = obj.get("agentName");
            if (agentName != null && agentName.isTextual()) {
                String resolved = nameMap.get(agentName.asText());
                if (resolved != null && !resolved.equals(agentName.asText())) {
                    obj.put("agentName", resolved);
                }
            }
            node.fields().forEachRemaining(e -> walk(e.getValue(), nameMap));
        } else if (node.isArray()) {
            for (JsonNode child : (ArrayNode) node) {
                walk(child, nameMap);
            }
        }
    }

    /**
     * Fingerprints of what already exists in the target environment.
     *
     * <p>These are built with the very same DTO builders the exporter uses —
     * that shared shape is what makes "already present, identical → skip"
     * reliable. Two independently-written hash formulas would drift and turn
     * every re-import into a pile of renamed duplicates.
     */
    private Map<String, String> existingAgentHashes(Long workspaceId) {
        Map<String, String> map = new LinkedHashMap<>();
        for (AgentEntity a : agentMapper.selectList(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getWorkspaceId, workspaceId)
                .eq(AgentEntity::getDeleted, 0))) {
            map.put(a.getName(), fingerprint.hash(fingerprint.agent(
                    a.getName(), a.getDescription(), a.getAgentType(), a.getRuntimeType(), a.getSystemPrompt(),
                    a.getIcon(), a.getTags(), a.getMaxIterations(), a.getEnabled(), a.getDefaultThinkingLevel(),
                    a.getModelName(), skillNamesOf(a.getId(), workspaceId), kbNamesOf(a.getId(), workspaceId),
                    toolNamesOf(a.getId()))));
        }
        return map;
    }

    private List<String> skillNamesOf(Long agentId, Long workspaceId) {
        List<String> names = new ArrayList<>();
        for (Long sid : bindingService.getBoundSkillIds(agentId)) {
            SkillEntity s = skillMapper.selectById(sid);
            if (s != null) {
                names.add(s.getName());
            }
        }
        return names;
    }

    private List<String> kbNamesOf(Long agentId, Long workspaceId) {
        List<String> names = new ArrayList<>();
        for (var binding : bindingService.listKbBindings(agentId)) {
            WikiKnowledgeBaseEntity kb = kbService.getById(binding.getKbId());
            if (kb != null) {
                names.add(kb.getName());
            }
        }
        return names;
    }

    private List<String> toolNamesOf(Long agentId) {
        Set<String> bound = bindingService.getBoundToolNames(agentId);
        return bound == null ? new ArrayList<>() : new ArrayList<>(bound);
    }

    private Map<String, String> existingSkillHashes(Long workspaceId) {
        Map<String, String> map = new LinkedHashMap<>();
        for (SkillEntity s : skillMapper.selectList(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getWorkspaceId, workspaceId)
                .eq(SkillEntity::getDeleted, 0))) {
            Map<String, String> files = new LinkedHashMap<>();
            for (var f : skillFileService.listBySkillId(s.getId())) {
                files.put(f.getFilePath(), f.getContent());
            }
            map.put(s.getName(), fingerprint.hash(fingerprint.skill(s.getName(), s.getNameZh(), s.getNameEn(),
                    s.getDescription(), s.getIcon(), s.getVersion(), s.getAuthor(), s.getTags(),
                    s.getSkillContent(), files)));
        }
        return map;
    }

    private Map<String, String> existingKbHashes(Long workspaceId, boolean includePages) {
        Map<String, String> map = new LinkedHashMap<>();
        for (WikiKnowledgeBaseEntity kb : kbService.listByWorkspace(workspaceId)) {
            List<Map<String, String>> raws = new ArrayList<>();
            for (var meta : rawService.listByKbId(kb.getId())) {
                var raw = rawService.getById(meta.getId());
                if (raw == null) {
                    continue;
                }
                String content = raw.getOriginalContent();
                if (content == null || content.isBlank()) {
                    continue;
                }
                raws.add(ordered("title", raw.getTitle(), "content", content));
            }
            map.put(kb.getName(), fingerprint.hash(fingerprint.knowledgeBase(
                    kb.getName(), kb.getDescription(), kb.getConfigContent(), raws,
                    includePages ? existingPagesOf(kb.getId()) : null)));
        }
        return map;
    }

    private List<Map<String, String>> existingPagesOf(Long kbId) {
        List<Map<String, String>> pages = new ArrayList<>();
        for (var page : wikiPageService.listByKbIdWithContent(kbId)) {
            if (page.getContent() == null || page.getContent().isBlank()) {
                continue;
            }
            if ("system".equalsIgnoreCase(String.valueOf(page.getPageType()))) {
                continue;
            }
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("slug", page.getSlug());
            entry.put("title", page.getTitle());
            entry.put("content", page.getContent());
            entry.put("summary", page.getSummary());
            entry.put("outgoingLinks", page.getOutgoingLinks());
            entry.put("pageType", page.getPageType());
            pages.add(entry);
        }
        pages.sort((a, b) -> String.valueOf(a.get("slug")).compareTo(String.valueOf(b.get("slug"))));
        return pages;
    }

    private boolean bundleCarriesPages(Bundle bundle) {
        for (JsonNode n : array(bundle, "knowledge-bases.json")) {
            JsonNode pages = n.path("pages");
            if (pages.isArray() && pages.size() > 0) {
                return true;
            }
        }
        return false;
    }

    private Map<String, String> existingWorkflowHashes(Long workspaceId) {
        Map<String, String> map = new LinkedHashMap<>();
        for (WorkflowEntity w : workflowService.listByWorkspace(workspaceId)) {
            WorkflowEntity full = workflowService.get(w.getId(), workspaceId);
            String graph = full == null ? null : full.getDraftJson();
            if ((graph == null || graph.isBlank()) && full != null && full.getLatestRevisionId() != null) {
                var revision = workflowRevisionMapper.selectById(full.getLatestRevisionId());
                graph = revision != null ? revision.getGraphJson() : null;
            }
            map.put(w.getName(), fingerprint.hash(fingerprint.workflow(
                    w.getName(), w.getDescription(), w.getEnabled(), graph)));
        }
        return map;
    }

    private Map<String, String> existingTriggerHashes(Long workspaceId) {
        Map<String, String> map = new LinkedHashMap<>();
        Map<Long, String> wfIdToName = new LinkedHashMap<>();
        for (WorkflowEntity w : workflowService.listByWorkspace(workspaceId)) {
            wfIdToName.put(w.getId(), w.getName());
        }
        for (TriggerEntity t : triggerService.listByWorkspace(workspaceId)) {
            if (!"workflow".equals(t.getTargetType())) {
                continue;
            }
            map.put(t.getName(), fingerprint.hash(fingerprint.trigger(t.getName(), t.getPatternType(),
                    t.getPatternJson(), t.getTargetType(), wfIdToName.get(Long.valueOf(String.valueOf(t.getTargetId()))),
                    t.getPayloadTemplate(), t.getRateLimitPerMin(), t.getDedupWindowSecs(),
                    t.getBotSelfFilter(), t.getEnabled())));
        }
        return map;
    }

    private Map<String, String> existingTeamHashes(Long workspaceId) {
        Map<String, String> map = new LinkedHashMap<>();
        Map<Long, String> agentIdToName = new LinkedHashMap<>();
        for (AgentEntity a : agentMapper.selectList(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getWorkspaceId, workspaceId)
                .eq(AgentEntity::getDeleted, 0))) {
            agentIdToName.put(a.getId(), a.getName());
        }
        for (AgentTeamEntity t : teamMapper.selectList(new LambdaQueryWrapper<AgentTeamEntity>()
                .eq(AgentTeamEntity::getWorkspaceId, workspaceId)
                .eq(AgentTeamEntity::getDeleted, 0))) {
            List<Map<String, String>> members = new ArrayList<>();
            for (AgentTeamMemberEntity m : memberMapper.selectList(new LambdaQueryWrapper<AgentTeamMemberEntity>()
                    .eq(AgentTeamMemberEntity::getTeamId, t.getId())
                    .eq(AgentTeamMemberEntity::getDeleted, 0))) {
                String memberName = agentIdToName.get(m.getAgentId());
                if (memberName != null) {
                    members.add(ordered("name", memberName, "role", m.getRole()));
                }
            }
            map.put(t.getName(), fingerprint.hash(fingerprint.team(
                    t.getName(), t.getDescription(), agentIdToName.get(t.getLeadAgentId()), members)));
        }
        return map;
    }

    private boolean alreadyInTeam(Long agentId) {
        return memberMapper.selectCount(new LambdaQueryWrapper<AgentTeamMemberEntity>()
                .eq(AgentTeamMemberEntity::getAgentId, agentId)
                .eq(AgentTeamMemberEntity::getDeleted, 0)) > 0;
    }

    private AgentEntity findAgentByName(Long workspaceId, String name) {
        return agentMapper.selectOne(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getWorkspaceId, workspaceId)
                .eq(AgentEntity::getName, name)
                .eq(AgentEntity::getDeleted, 0));
    }

    private SkillEntity findSkillByName(Long workspaceId, String name) {
        return skillMapper.selectOne(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getWorkspaceId, workspaceId)
                .eq(SkillEntity::getName, name)
                .eq(SkillEntity::getDeleted, 0));
    }

    private WikiKnowledgeBaseEntity findKbByName(Long workspaceId, String name) {
        return kbService.listByWorkspace(workspaceId).stream()
                .filter(k -> k.getName().equals(name)).findFirst().orElse(null);
    }

    // Batch stamping for rows whose create path doesn't carry the column yet.
    // Written through a wrapper (not entity.updateById()) because these
    // entities are plain POJOs rather than MyBatis-Plus Model subclasses.
    private void stampKbBatch(Long kbId, String batchId) {
        kbMapper.update(null, new LambdaUpdateWrapper<WikiKnowledgeBaseEntity>()
                .eq(WikiKnowledgeBaseEntity::getId, kbId)
                .set(WikiKnowledgeBaseEntity::getImportBatch, batchId));
    }

    private void stampTeamBatch(Long teamId, String batchId) {
        teamMapper.update(null, new LambdaUpdateWrapper<AgentTeamEntity>()
                .eq(AgentTeamEntity::getId, teamId)
                .set(AgentTeamEntity::getImportBatch, batchId));
    }

    private int deleteAgentsByBatch(Long workspaceId, String batchId) {
        List<AgentEntity> rows = agentMapper.selectList(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getImportBatch, batchId)
                .eq(AgentEntity::getWorkspaceId, workspaceId));
        for (AgentEntity a : rows) {
            bindingService.setSkillBindings(a.getId(), List.of());
            bindingService.setKbBindings(a.getId(), List.of());
        }
        return agentMapper.delete(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getImportBatch, batchId)
                .eq(AgentEntity::getWorkspaceId, workspaceId));
    }

    private int deleteSkillsByBatch(Long workspaceId, String batchId) {
        List<SkillEntity> rows = skillMapper.selectList(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getImportBatch, batchId)
                .eq(SkillEntity::getWorkspaceId, workspaceId));
        for (SkillEntity s : rows) {
            skillFileService.deleteAllForSkill(s.getId());
        }
        return skillMapper.delete(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getImportBatch, batchId)
                .eq(SkillEntity::getWorkspaceId, workspaceId));
    }

    private int deleteKbsByBatch(Long workspaceId, String batchId) {
        List<WikiKnowledgeBaseEntity> rows = kbService.listByWorkspace(workspaceId).stream()
                .filter(k -> batchId.equals(k.getImportBatch())).toList();
        for (WikiKnowledgeBaseEntity kb : rows) {
            kbService.delete(kb.getId());
        }
        return rows.size();
    }

    private int deleteWorkflowsByBatch(Long workspaceId, String batchId) {
        List<WorkflowEntity> rows = workflowService.listByWorkspace(workspaceId).stream()
                .filter(w -> batchId.equals(w.getImportBatch())).toList();
        for (WorkflowEntity w : rows) {
            workflowService.delete(w.getId(), workspaceId);
        }
        return rows.size();
    }

    private int deleteTeamsByBatch(Long workspaceId, String batchId) {
        return teamMapper.delete(new LambdaQueryWrapper<AgentTeamEntity>()
                .eq(AgentTeamEntity::getImportBatch, batchId)
                .eq(AgentTeamEntity::getWorkspaceId, workspaceId));
    }

    /**
     * Map.of() iteration order is randomized per JVM (immutable-map SALT), which
     * would make a bundle exported on one JVM hash differently on another and
     * turn every re-import into a false "content differs" conflict. Always build
     * these small maps with a stable order instead.
     */
    private static Map<String, String> ordered(String k1, String v1, String k2, String v2) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
