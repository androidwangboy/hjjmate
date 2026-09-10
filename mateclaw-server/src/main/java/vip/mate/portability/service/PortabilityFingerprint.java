package vip.mate.portability.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for how a portable entity is fingerprinted.
 *
 * <p>Export and import <b>must</b> hash the same canonical shape, otherwise the
 * "already exists with identical content → skip" decision silently degrades
 * into "rename" for every entity and repeated imports pile up duplicates.
 * Both sides therefore build their DTO through this component and hash it with
 * the same canonical JSON serializer.
 *
 * <p>Field insertion order is part of the contract: {@link LinkedHashMap} keeps
 * the JSON stable across JVM runs.
 */
@Component
@RequiredArgsConstructor
public class PortabilityFingerprint {

    private final ObjectMapper objectMapper;

    public Map<String, Object> agent(String name, String description, String agentType, String runtimeType,
                                     String systemPrompt, String icon, String tags, Integer maxIterations,
                                     Boolean enabled, String defaultThinkingLevel, String modelName,
                                     List<String> skills, List<String> knowledgeBases, List<String> tools) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("name", name);
        dto.put("description", description);
        dto.put("agentType", agentType);
        dto.put("runtimeType", runtimeType);
        dto.put("systemPrompt", systemPrompt);
        dto.put("icon", icon);
        dto.put("tags", tags);
        dto.put("maxIterations", maxIterations);
        dto.put("enabled", enabled);
        dto.put("defaultThinkingLevel", defaultThinkingLevel);
        if (modelName != null && !modelName.isBlank()) {
            dto.put("modelName", modelName);
        }
        dto.put("skills", sorted(skills));
        dto.put("knowledgeBases", sorted(knowledgeBases));
        dto.put("tools", sorted(tools));
        return dto;
    }

    public Map<String, Object> skill(String name, String nameZh, String nameEn, String description, String icon,
                                     String version, String author, String tags, String skillContent,
                                     Map<String, String> files) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("name", name);
        dto.put("nameZh", nameZh);
        dto.put("nameEn", nameEn);
        dto.put("description", description);
        dto.put("icon", icon);
        dto.put("version", version);
        dto.put("author", author);
        dto.put("tags", tags);
        dto.put("skillContent", skillContent);
        dto.put("files", files == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<>(files));
        return dto;
    }

    public Map<String, Object> knowledgeBase(String name, String description, String configContent,
                                             List<Map<String, String>> raws, List<Map<String, String>> pages) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("name", name);
        dto.put("description", description);
        dto.put("configContent", configContent);
        dto.put("raws", raws == null ? new ArrayList<Map<String, String>>() : raws);
        // Pages are only part of the fingerprint when the bundle actually
        // carries them (includePages=true); otherwise both sides hash null.
        dto.put("pages", pages == null ? new ArrayList<Map<String, String>>() : pages);
        return dto;
    }

    /** One expert's memory files, keyed by filename — used by the memory module. */
    public Map<String, Object> memory(String agentName, Map<String, String> files) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("agent", agentName);
        dto.put("files", files == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<>(files));
        return dto;
    }

    public Map<String, Object> workflow(String name, String description, Boolean enabled, String graphJson) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("name", name);
        dto.put("description", description);
        dto.put("enabled", enabled);
        // Graph text is normalized before hashing: whitespace/formatting and the
        // transport-only employeeName hint must never make two identical
        // workflows look different.
        dto.put("graphJson", canonicalGraph(graphJson));
        return dto;
    }

    /**
     * Canonical form of a workflow graph for fingerprinting: re-serialized
     * through Jackson (kills incidental whitespace) with every
     * {@code mode.employeeName} hint removed. employeeName is import transport
     * metadata, not content — including it would make a bundle look different
     * from the very environment it was exported from.
     */
    private String canonicalGraph(String graphJson) {
        if (graphJson == null || graphJson.isBlank()) {
            return graphJson;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(graphJson);
            com.fasterxml.jackson.databind.JsonNode steps = root.path("steps");
            if (steps.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode step : steps) {
                    com.fasterxml.jackson.databind.JsonNode mode = step.path("mode");
                    if (mode.isObject() && mode.has("employeeName")) {
                        ((com.fasterxml.jackson.databind.node.ObjectNode) mode).remove("employeeName");
                    }
                }
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return graphJson;
        }
    }

    public Map<String, Object> trigger(String name, String patternType, String patternJson, String targetType,
                                       String targetWorkflow, String payloadTemplate, Integer rateLimitPerMin,
                                       Integer dedupWindowSecs, Boolean botSelfFilter, Boolean enabled) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("name", name);
        dto.put("patternType", patternType);
        dto.put("patternJson", patternJson);
        dto.put("targetType", targetType);
        dto.put("targetWorkflow", targetWorkflow);
        dto.put("payloadTemplate", payloadTemplate);
        dto.put("rateLimitPerMin", rateLimitPerMin);
        dto.put("dedupWindowSecs", dedupWindowSecs);
        dto.put("botSelfFilter", botSelfFilter);
        dto.put("enabled", enabled);
        return dto;
    }

    public Map<String, Object> team(String name, String description, String lead, List<Map<String, String>> members) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("name", name);
        dto.put("description", description);
        dto.put("lead", lead);
        dto.put("members", members == null ? new ArrayList<Map<String, String>>() : members);
        return dto;
    }

    /** Canonical JSON → sha256. The DTO must not carry its own contentHash. */
    public String hash(Map<String, Object> dto) {
        Map<String, Object> copy = new LinkedHashMap<>(dto);
        copy.remove("contentHash");
        try {
            return sha256(objectMapper.writeValueAsString(copy));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fingerprint portable entity", e);
        }
    }

    private static List<String> sorted(List<String> values) {
        List<String> copy = values == null ? new ArrayList<>() : new ArrayList<>(values);
        copy.sort(String::compareTo);
        return copy;
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
