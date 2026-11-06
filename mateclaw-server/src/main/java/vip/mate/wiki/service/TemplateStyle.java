package vip.mate.wiki.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板"样式"的结构化描述。
 * <p>
 * 与"内容(content_json)"解耦：样式只描述布局/字段骨架/表格结构，用于判断
 * 两个模板"长得像不像"。同一份样式可被多份内容复用，因此样式与内容分开存储、
 * 在召回时按需组合（见 WikiRecordTemplateEntity）。
 *
 * <p>用法：
 * <ul>
 *   <li>{@link #toSignatureJson()} —— 规范化的结构 JSON，落库 + 作为指纹输入</li>
 *   <li>{@link #toStyleText()}      —— 供 embedding 的可读文本（强调结构而非字面内容）</li>
 *   <li>{@link #sha256()}           —— 归一化样式指纹，用于精确去重/精确匹配</li>
 * </ul>
 */
public class TemplateStyle {

    private static final ObjectMapper SIGNATURE_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    /** 来源格式：docx / xlsx / pdf / json（手动录入时由 content_json 推导） */
    private String format;

    /** 章节 / 表头分组 / sheet 名（体现层级与分段） */
    private List<String> sections = new ArrayList<>();

    /** 字段（表头列、表单域、JSON key），含推断类型 */
    private List<Field> fields = new ArrayList<>();

    /** 表格（行×列 + 表头），Excel 每个 sheet、Word 每个表格都会成一条 */
    private List<Table> tables = new ArrayList<>();

    /** 布局类型：outline / form_with_tables / grid_sheet / mixed / unknown */
    private String layoutType;

    /** 其它格式相关属性（如 pdf 页数、sheet 数、acroform 标志），不参与哈希 */
    private Map<String, Object> extras = new LinkedHashMap<>();

    public record Field(String name, String type) {}

    public record Table(int rows, int cols, List<String> header) {}

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public List<String> getSections() { return sections; }
    public void setSections(List<String> sections) { this.sections = sections; }
    public List<Field> getFields() { return fields; }
    public void setFields(List<Field> fields) { this.fields = fields; }
    public List<Table> getTables() { return tables; }
    public void setTables(List<Table> tables) { this.tables = tables; }
    public String getLayoutType() { return layoutType; }
    public void setLayoutType(String layoutType) { this.layoutType = layoutType; }
    public Map<String, Object> getExtras() { return extras; }
    public void setExtras(Map<String, Object> extras) { this.extras = extras; }

    /** 推断布局类型（在填充完 sections/fields/tables 后调用） */
    public void inferLayoutType() {
        boolean hasTables = !tables.isEmpty();
        boolean hasSections = !sections.isEmpty();
        boolean hasFields = !fields.isEmpty();
        if (hasTables && (hasSections || hasFields)) {
            this.layoutType = "form_with_tables";
        } else if (hasTables) {
            this.layoutType = "grid_sheet";
        } else if (hasSections || hasFields) {
            this.layoutType = "outline";
        } else {
            this.layoutType = "unknown";
        }
    }

    /** 规范化结构签名（JSON），用于落库与哈希 */
    public String toSignatureJson() {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("format", format == null ? "unknown" : format);
            root.put("layoutType", layoutType == null ? "unknown" : layoutType);
            root.put("sections", sections == null ? List.of() : sections);
            List<Map<String, String>> flds = new ArrayList<>();
            if (fields != null) {
                for (Field f : fields) {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("name", f.name() == null ? "" : f.name());
                    m.put("type", f.type() == null ? "text" : f.type());
                    flds.add(m);
                }
            }
            root.put("fields", flds);
            List<Map<String, Object>> tbls = new ArrayList<>();
            if (tables != null) {
                for (Table t : tables) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("rows", t.rows());
                    m.put("cols", t.cols());
                    m.put("header", t.header() == null ? List.of() : t.header());
                    tbls.add(m);
                }
            }
            root.put("tables", tbls);
            return SIGNATURE_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            // 极端兜底：退化为格式+布局字符串，保证仍可落库
            return "{\"format\":\"" + (format == null ? "unknown" : format) + "\"}";
        }
    }

    /**
     * 供 embedding 的"样式描述文本"。刻意强调结构（格式、布局、章节名、字段名与类型、
     * 表格行列），弱化字面内容，使"同一套布局、不同内容"的模板在向量空间里聚拢。
     */
    public String toStyleText() {
        StringBuilder sb = new StringBuilder();
        sb.append("format: ").append(format == null ? "unknown" : format).append('\n');
        sb.append("layout: ").append(layoutType == null ? "unknown" : layoutType).append('\n');
        if (sections != null && !sections.isEmpty()) {
            sb.append("sections: ").append(String.join(", ", sections)).append('\n');
        }
        if (fields != null && !fields.isEmpty()) {
            List<String> flds = new ArrayList<>();
            for (Field f : fields) {
                flds.add((f.name() == null ? "" : f.name()) + "(" + (f.type() == null ? "text" : f.type()) + ")");
            }
            sb.append("fields: ").append(String.join(", ", flds)).append('\n');
        }
        if (tables != null && !tables.isEmpty()) {
            sb.append("tables: ").append(tables.size());
            List<String> dims = new ArrayList<>();
            for (Table t : tables) {
                dims.add(t.rows() + "x" + t.cols());
            }
            sb.append(" (rows x cols): ").append(String.join(", ", dims)).append('\n');
        }
        return sb.toString().trim();
    }

    /** 归一化样式指纹（SHA-256，大写十六进制），用于精确去重/精确匹配 */
    public String sha256() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(toSignatureJson().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02X", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
