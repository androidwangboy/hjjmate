package vip.mate.portability.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manifest of a {@code .mcbundle} — the portable configuration-asset package
 * produced by the export endpoint and consumed by the import endpoints.
 *
 * <p>The manifest is the contract: it pins the format version so an old
 * bundle can be rejected explicitly (instead of half-imported), advertises
 * what the bundle contains, and carries per-file checksums so a truncated or
 * tampered upload fails before a single row is written.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BundleManifest {

    public static final String FORMAT = "mcbundle";
    public static final String SCHEMA_VERSION = "1.0";

    private String format = FORMAT;
    private String schemaVersion = SCHEMA_VERSION;
    private String bundleName;
    private String generatorVersion;
    private String exportedAt = Instant.now().toString();
    /** Non-sensitive description of the source only — never hostnames, URLs or keys. */
    private String sourceWorkspace;
    private List<String> modules = List.of();
    private Map<String, Integer> counts = new LinkedHashMap<>();
    /** path -> "sha256:<hex>" for every payload file in the bundle. */
    private Map<String, String> checksums = new LinkedHashMap<>();
    /** Environment-coupled items discovered at export time (channels, models…). */
    private List<String> warnings = List.of();

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getBundleName() {
        return bundleName;
    }

    public void setBundleName(String bundleName) {
        this.bundleName = bundleName;
    }

    public String getGeneratorVersion() {
        return generatorVersion;
    }

    public void setGeneratorVersion(String generatorVersion) {
        this.generatorVersion = generatorVersion;
    }

    public String getExportedAt() {
        return exportedAt;
    }

    public void setExportedAt(String exportedAt) {
        this.exportedAt = exportedAt;
    }

    public String getSourceWorkspace() {
        return sourceWorkspace;
    }

    public void setSourceWorkspace(String sourceWorkspace) {
        this.sourceWorkspace = sourceWorkspace;
    }

    public List<String> getModules() {
        return modules;
    }

    public void setModules(List<String> modules) {
        this.modules = modules;
    }

    public Map<String, Integer> getCounts() {
        return counts;
    }

    public void setCounts(Map<String, Integer> counts) {
        this.counts = counts;
    }

    public Map<String, String> getChecksums() {
        return checksums;
    }

    public void setChecksums(Map<String, String> checksums) {
        this.checksums = checksums;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
