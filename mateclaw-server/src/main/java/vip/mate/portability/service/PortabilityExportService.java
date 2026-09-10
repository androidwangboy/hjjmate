package vip.mate.portability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.agent.AgentService;
import vip.mate.agent.binding.service.AgentBindingService;
import vip.mate.agent.model.AgentEntity;
import vip.mate.agent.repository.AgentMapper;
import vip.mate.portability.model.BundleManifest;
import vip.mate.skill.model.SkillEntity;
import vip.mate.skill.model.SkillFileEntity;
import vip.mate.skill.repository.SkillMapper;
import vip.mate.skill.service.SkillFileService;
import vip.mate.team.model.AgentTeamEntity;
import vip.mate.team.repository.AgentTeamMapper;
import vip.mate.team.repository.AgentTeamMemberMapper;
import vip.mate.team.model.AgentTeamMemberEntity;
import vip.mate.trigger.model.TriggerEntity;
import vip.mate.trigger.repository.TriggerMapper;
import vip.mate.trigger.service.TriggerService;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;
import vip.mate.wiki.model.WikiRawMaterialEntity;
import vip.mate.wiki.service.WikiKnowledgeBaseService;
import vip.mate.wiki.service.WikiRawMaterialService;
import vip.mate.workflow.model.WorkflowEntity;
import vip.mate.workflow.service.WorkflowService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a {@code .mcbundle}: a portable, environment-independent snapshot of
 * business configuration assets (experts, teams, skills, knowledge bases,
 * workflows, triggers).
 *
 * <p><b>Whitelist mapping, never row serialization.</b> Every exported field is
 * copied explicitly. IDs, workspace scoping, credentials, security-scan state
 * and any run-time column are structurally absent from the bundle — that is
 * what makes it safe to hand a dev-environment bundle to production.
 *
 * <p><b>References travel by name.</b> A skill / KB / tool / workflow / agent
 * reference is written as its name so the target environment can re-resolve it
 * against its own IDs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortabilityExportService {

    private static final int MAX_RAW_CHARS = 2_000_000;

    /**
     * Memory files that travel with a bundle. SOUL.md is deliberately excluded:
     * it holds environment-flavoured persona preferences that should not leak
     * into another deployment.
     */
    private static final Set<String> MEMORY_FILE_WHITELIST =
            Set.of("AGENTS.md", "MEMORY.md", "PROFILE.md", "KNOWLEDGE.md");

    private final AgentService agentService;
    private final AgentMapper agentMapper;
    private final AgentBindingService bindingService;
    private final SkillMapper skillMapper;
    private final SkillFileService skillFileService;
    private final WikiKnowledgeBaseService kbService;
    private final WikiRawMaterialService rawService;
    private final vip.mate.wiki.service.WikiPageService wikiPageService;
    private final vip.mate.workspace.document.WorkspaceFileService workspaceFileService;
    private final vip.mate.workflow.repository.WorkflowRevisionMapper workflowRevisionMapper;
    private final WorkflowService workflowService;
    private final TriggerService triggerService;
    private final AgentTeamMapper teamMapper;
    private final AgentTeamMemberMapper memberMapper;
    private final TriggerMapper triggerMapper;
    private final ObjectMapper objectMapper;
    private final PortabilityFingerprint fingerprint;

    /**
     * @param workspaceId          owning workspace
     * @param modules              which modules to include (empty = all)
     * @param agentNames           when non-empty, export only these experts and
     *                             auto-include every dependency they need
     * @param includeKnowledgeBase whether to include KB definitions + raw material
     */
    public byte[] exportBundle(Long workspaceId, Set<String> modules, Set<String> agentNames,
                               boolean includeKnowledgeBase, boolean includePages,
                               boolean includeMemory) throws IOException {
        boolean all = modules == null || modules.isEmpty();
        boolean wantAgents = all || modules.contains("agents");
        boolean wantTeams = all || modules.contains("teams");
        boolean wantSkills = all || modules.contains("skills");
        boolean wantKbs = (all || modules.contains("kbs")) && includeKnowledgeBase;
        boolean wantWorkflows = all || modules.contains("workflows");
        boolean wantTriggers = all || modules.contains("triggers");
        boolean wantMemory = (all || modules.contains("memory")) && includeMemory;

        List<Map<String, Object>> agents = new ArrayList<>();
        List<Map<String, Object>> teams = new ArrayList<>();
        List<Map<String, Object>> skills = new ArrayList<>();
        List<Map<String, Object>> kbs = new ArrayList<>();
        List<Map<String, Object>> workflows = new ArrayList<>();
        List<Map<String, Object>> triggers = new ArrayList<>();
        List<Map<String, Object>> memories = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Map<Long, String> agentIdToName = new LinkedHashMap<>();
        for (AgentEntity a : agentService.listAgentsByWorkspace(workspaceId)) {
            agentIdToName.put(a.getId(), a.getName());
        }

        // ---- Experts -----------------------------------------------------
        List<AgentEntity> agentRows = new ArrayList<>();
        if (wantAgents) {
            for (AgentEntity a : agentService.listAgentsByWorkspace(workspaceId)) {
                if (!agentNames.isEmpty() && !agentNames.contains(a.getName())) {
                    continue;
                }
                agentRows.add(a);
            }
        }
        for (AgentEntity a : agentRows) {
            // Portable references — names, never IDs.
            List<String> skillRefs = new ArrayList<>();
            for (Long sid : bindingService.getBoundSkillIds(a.getId())) {
                SkillEntity s = skillMapper.selectById(sid);
                if (s != null) {
                    skillRefs.add(s.getName());
                }
            }
            List<String> kbRefs = new ArrayList<>();
            for (var binding : bindingService.listKbBindings(a.getId())) {
                WikiKnowledgeBaseEntity bound = kbService.getById(binding.getKbId());
                if (bound != null) {
                    kbRefs.add(bound.getName());
                }
            }
            // getBoundToolNames() returns null for "no explicit rows → inherit
            // global default"; treat that as "no explicit tool list exported".
            Set<String> boundTools = bindingService.getBoundToolNames(a.getId());
            List<String> toolRefs = boundTools == null ? new ArrayList<>() : new ArrayList<>(boundTools);
            Map<String, Object> dto = fingerprint.agent(a.getName(), a.getDescription(), a.getAgentType(),
                    a.getRuntimeType(), a.getSystemPrompt(), a.getIcon(), a.getTags(), a.getMaxIterations(),
                    a.getEnabled(), a.getDefaultThinkingLevel(), a.getModelName(), skillRefs, kbRefs, toolRefs);
            dto.put("contentHash", fingerprint.hash(dto));
            agents.add(dto);
        }

        // ---- Teams (only those whose lead survived the filter) -----------
        if (wantTeams) {
            List<AgentTeamEntity> teamRows = teamMapper.selectList(
                    new LambdaQueryWrapper<AgentTeamEntity>()
                            .eq(AgentTeamEntity::getWorkspaceId, workspaceId)
                            .eq(AgentTeamEntity::getDeleted, 0));
            Set<String> exportedAgents = new java.util.HashSet<>();
            for (Map<String, Object> a : agents) {
                exportedAgents.add(String.valueOf(a.get("name")));
            }
            for (AgentTeamEntity t : teamRows) {
                String lead = agentIdToName.get(t.getLeadAgentId());
                if (!exportedAgents.isEmpty() && (lead == null || !exportedAgents.contains(lead))) {
                    continue;
                }
                List<Map<String, String>> members = new ArrayList<>();
                for (AgentTeamMemberEntity m : memberMapper.selectList(
                        new LambdaQueryWrapper<AgentTeamMemberEntity>()
                                .eq(AgentTeamMemberEntity::getTeamId, t.getId())
                                .eq(AgentTeamMemberEntity::getDeleted, 0))) {
                    String memberName = agentIdToName.get(m.getAgentId());
                    if (memberName == null) {
                        continue;
                    }
                    members.add(ordered("name", memberName, "role", m.getRole()));
                }
                Map<String, Object> dto = fingerprint.team(t.getName(), t.getDescription(), lead, members);
                dto.put("contentHash", fingerprint.hash(dto));
                teams.add(dto);
            }
        }

        // ---- Skills: those referenced by exported experts (or all) -------
        if (wantSkills) {
            Set<String> needed = new java.util.HashSet<>();
            if (agents.isEmpty()) {
                needed.add("*");
            } else {
                for (Map<String, Object> a : agents) {
                    Object refs = a.get("skills");
                    if (refs instanceof List<?> list) {
                        for (Object o : list) {
                            needed.add(String.valueOf(o));
                        }
                    }
                }
            }
            for (SkillEntity s : skillMapper.selectList(new LambdaQueryWrapper<SkillEntity>()
                    .eq(SkillEntity::getWorkspaceId, workspaceId)
                    .eq(SkillEntity::getDeleted, 0))) {
                if (!needed.contains("*") && !needed.contains(s.getName())) {
                    continue;
                }
                if (Boolean.TRUE.equals(s.getBuiltin())) {
                    continue; // built-ins ship with the app; never migrate them
                }
                Map<String, String> files = new LinkedHashMap<>();
                for (SkillFileEntity f : skillFileService.listBySkillId(s.getId())) {
                    files.put(f.getFilePath(), f.getContent());
                }
                Map<String, Object> dto = fingerprint.skill(s.getName(), s.getNameZh(), s.getNameEn(),
                        s.getDescription(), s.getIcon(), s.getVersion(), s.getAuthor(), s.getTags(),
                        s.getSkillContent(), files);
                dto.put("contentHash", fingerprint.hash(dto));
                skills.add(dto);
            }
        }

        // ---- Knowledge bases: definitions + raw material (source of truth) --
        if (wantKbs) {
            Set<String> needed = new java.util.HashSet<>();
            for (Map<String, Object> a : agents) {
                Object refs = a.get("knowledgeBases");
                if (refs instanceof List<?> list) {
                    for (Object o : list) {
                        needed.add(String.valueOf(o));
                    }
                }
            }
            for (WikiKnowledgeBaseEntity kb : kbService.listByWorkspace(workspaceId)) {
                if (!agents.isEmpty() && !needed.contains(kb.getName())) {
                    continue;
                }
                List<Map<String, String>> raws = new ArrayList<>();
                for (WikiRawMaterialEntity meta : rawService.listByKbId(kb.getId())) {
                    // listByKbId strips content for transport — re-fetch each row
                    // so the bundle carries the actual source text.
                    WikiRawMaterialEntity raw = rawService.getById(meta.getId());
                    if (raw == null) {
                        continue;
                    }
                    String content = raw.getOriginalContent();
                    if (content == null || content.isBlank()) {
                        continue;
                    }
                    if (content.length() > MAX_RAW_CHARS) {
                        warnings.add("KB「" + kb.getName() + "」原料「" + raw.getTitle()
                                + "」超过 " + MAX_RAW_CHARS + " 字符，已截断");
                        content = content.substring(0, MAX_RAW_CHARS);
                    }
                    raws.add(ordered("title", raw.getTitle(), "content", content));
                }
                List<Map<String, String>> pages = new ArrayList<>();
                if (includePages) {
                    for (var page : wikiPageService.listByKbIdWithContent(kb.getId())) {
                        if (page.getContent() == null || page.getContent().isBlank()) {
                            continue;
                        }
                        // System pages (Index / Log) are regenerated per KB —
                        // carrying them across environments is meaningless.
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
                }
                Map<String, Object> dto = fingerprint.knowledgeBase(kb.getName(), kb.getDescription(),
                        kb.getConfigContent(), raws, includePages ? pages : null);
                dto.put("contentHash", fingerprint.hash(dto));
                kbs.add(dto);
            }
        }

        // ---- Workflows ---------------------------------------------------
        if (wantWorkflows) {
            for (WorkflowEntity w : workflowService.listByWorkspace(workspaceId)) {
                WorkflowEntity full = workflowService.get(w.getId(), workspaceId);
                // publish() clears the draft and publishedGraphJson is a
                // transient field, so a published-only workflow must be read
                // from its latest revision — otherwise every workflow is
                // silently skipped and the bundle exports zero flows.
                String graph = full.getDraftJson();
                if ((graph == null || graph.isBlank()) && full.getLatestRevisionId() != null) {
                    var revision = workflowRevisionMapper.selectById(full.getLatestRevisionId());
                    graph = revision != null ? revision.getGraphJson() : null;
                }
                if (graph == null || graph.isBlank()) {
                    continue;
                }
                // P1: a write_memory step carries an employeeId snowflake that
                // means nothing in another environment. Resolve it to the
                // expert's name and carry both — import re-resolves the name.
                graph = tagWriteMemoryEmployee(graph, workspaceId);
                Map<String, Object> dto = fingerprint.workflow(w.getName(), w.getDescription(), w.getEnabled(), graph);
                // Environment-coupled channel references must be re-pointed manually.
                if (graph.contains("dispatch_channel") || graph.contains("approverChannels")) {
                    warnings.add("工作流「" + w.getName() + "」引用了渠道 ID（dispatch_channel / approverChannels），"
                            + "导入后需重新配置渠道");
                }
                if (graph.contains("employeeId")) {
                    warnings.add("工作流「" + w.getName() + "」的 write_memory step 含 employeeId（雪花 ID），"
                            + "导入后需重新选择专家");
                }
                dto.put("contentHash", fingerprint.hash(dto));
                workflows.add(dto);
            }
        }

        // ---- Triggers ---------------------------------------------------
        if (wantTriggers) {
            Map<Long, String> wfIdToName = new LinkedHashMap<>();
            for (WorkflowEntity w : workflowService.listByWorkspace(workspaceId)) {
                wfIdToName.put(w.getId(), w.getName());
            }
            for (TriggerEntity t : triggerService.listByWorkspace(workspaceId)) {
                if (!"workflow".equals(t.getTargetType())) {
                    continue; // agent targets are ID-based; P0 exports workflow targets only
                }
                String wfName = wfIdToName.get(Long.valueOf(String.valueOf(t.getTargetId())));
                if (wfName == null) {
                    continue;
                }
                Map<String, Object> dto = fingerprint.trigger(t.getName(), t.getPatternType(), t.getPatternJson(),
                        t.getTargetType(), wfName, t.getPayloadTemplate(), t.getRateLimitPerMin(),
                        t.getDedupWindowSecs(), t.getBotSelfFilter(), t.getEnabled());
                dto.put("contentHash", fingerprint.hash(dto));
                triggers.add(dto);
            }
        }

        // ---- Expert memory files -----------------------------------------
        if (wantMemory) {
            for (AgentEntity a : agentRows) {
                Map<String, String> files = new LinkedHashMap<>();
                for (var meta : workspaceFileService.listFiles(a.getId())) {
                    String name = meta.getFilename();
                    if (name == null || !MEMORY_FILE_WHITELIST.contains(name)) {
                        continue;
                    }
                    var full = workspaceFileService.getFile(a.getId(), name);
                    if (full != null && full.getContent() != null && !full.getContent().isBlank()) {
                        files.put(name, full.getContent());
                    }
                }
                if (files.isEmpty()) {
                    continue;
                }
                Map<String, Object> dto = fingerprint.memory(a.getName(), files);
                dto.put("contentHash", fingerprint.hash(dto));
                memories.add(dto);
            }
        }

        // ---- Assemble ----------------------------------------------------
        BundleManifest manifest = new BundleManifest();
        manifest.setBundleName("assets-" + java.time.LocalDate.now());
        manifest.setGeneratorVersion(resolveAppVersion());
        manifest.setSourceWorkspace(String.valueOf(workspaceId));
        manifest.setModules(List.of("agents", "teams", "skills", "kbs", "workflows", "triggers", "memory"));
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("agents", agents.size());
        counts.put("teams", teams.size());
        counts.put("skills", skills.size());
        counts.put("knowledgeBases", kbs.size());
        counts.put("workflows", workflows.size());
        counts.put("triggers", triggers.size());
        counts.put("memorySets", memories.size());
        manifest.setCounts(counts);
        manifest.setWarnings(warnings);

        Map<String, String> payloads = new LinkedHashMap<>();
        payloads.put("agents.json", writeJson(agents));
        payloads.put("teams.json", writeJson(teams));
        payloads.put("skills.json", writeJson(skills));
        payloads.put("knowledge-bases.json", writeJson(kbs));
        payloads.put("workflows.json", writeJson(workflows));
        payloads.put("triggers.json", writeJson(triggers));
        payloads.put("agent-memory.json", writeJson(memories));

        Map<String, String> checksums = new LinkedHashMap<>();
        payloads.forEach((path, body) -> checksums.put(path, "sha256:" + hash(body)));
        manifest.setChecksums(checksums);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            put(zos, "manifest.json", writeJson(manifest));
            payloads.forEach((path, body) -> put(zos, path, body));
        }
        log.info("[portability] exported bundle: {}", counts);
        return bos.toByteArray();
    }


    /**
     * Adds {@code mode.employeeName} next to every {@code write_memory}
     * step whose {@code employeeId} is a literal snowflake. Template values
     * ({{ ... }}) are left untouched — they resolve at run time.
     */
    private String tagWriteMemoryEmployee(String graphJson, Long workspaceId) {
        try {
            JsonNode root = objectMapper.readTree(graphJson);
            JsonNode steps = root.path("steps");
            if (!steps.isArray()) {
                return graphJson;
            }
            boolean changed = false;
            for (JsonNode step : steps) {
                JsonNode mode = step.path("mode");
                if (!"write_memory".equals(mode.path("type").asText())) {
                    continue;
                }
                String employeeId = mode.path("employeeId").asText("");
                if (employeeId.isBlank() || employeeId.contains("{{")) {
                    continue;
                }
                AgentEntity agent = agentMapper.selectById(Long.valueOf(employeeId));
                if (agent != null) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) mode).put("employeeName", agent.getName());
                    changed = true;
                }
            }
            // Re-serializing rewrites formatting for every graph; only do it
            // when a tag was actually added so untouched graphs stay byte-identical.
            return changed ? objectMapper.writeValueAsString(root) : graphJson;
        } catch (Exception e) {
            log.warn("[portability] could not tag write_memory employeeName: {}", e.getMessage());
            return graphJson;
        }
    }

    private static void put(ZipOutputStream zos, String path, String content) {
        try {
            zos.putNextEntry(new ZipEntry(path));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write bundle entry " + path, e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize bundle payload", e);
        }
    }

    private String canonical(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> withoutHash(Map<String, Object> dto) {
        Map<String, Object> copy = new LinkedHashMap<>(dto);
        copy.remove("contentHash");
        return copy;
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

    private static String hash(String input) {
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

    private String resolveAppVersion() {
        String v = getClass().getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }
}
