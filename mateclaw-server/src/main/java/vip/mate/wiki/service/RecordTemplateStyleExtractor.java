package vip.mate.wiki.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import vip.mate.tool.builtin.TikaExtractor;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 从模板文件（docx / xlsx / pdf）中抽取"样式"结构。
 * <p>
 * 关键点：抽取的是<b>结构</b>（章节、字段、表格行列），不是文字内容。这样不同科室、
 * 不同内容的模板只要"长一样"就会产生相近的 style_embedding，满足"召回样式类似模板"。
 *
 * <p>约束：样式只能由上传文件抽取，<b>绝不</b>从 content_json 推导（content_json 是模板内容，
 * 由人工录入并同行绑定）。
 *
 * <p>PDF 没有可靠的 DOM 结构，采用"Tika 文本 + 启发式章节识别 + PDFBox 表单域"的
 * 尽力而为策略；docx / xlsx 走 Apache POI 直接读对象模型，结构最可靠。
 */
@Slf4j
public class RecordTemplateStyleExtractor {

    /** 章节启发式：编号标题（1. 主诉 / 一、既往史）或短标签行 */
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^\\s*([0-9]+[.、]|[一二三四五六七八九十]+[.、、])\\s*\\S+");
    private static final int MAX_HEADING_LEN = 30;

    /**
     * 从已落盘的文件抽取样式。
     *
     * @param file       绝对路径（服务器已暂存）
     * @param sourceType docx / xlsx / pdf
     */
    public TemplateStyle extract(Path file, String sourceType) {
        if (sourceType == null) sourceType = "unknown";
        return switch (sourceType.toLowerCase()) {
            case "docx", "doc" -> extractDocx(file);
            case "xlsx", "xls" -> extractXlsx(file);
            case "pdf" -> extractPdf(file);
            default -> {
                log.warn("[TemplateStyle] 不支持的模板格式: {}，回退为未知样式", sourceType);
                TemplateStyle s = new TemplateStyle();
                s.setFormat(sourceType);
                s.inferLayoutType();
                yield s;
            }
        };
    }

    /**
     * 抽取源文件的纯文本内容（用于前端预览），与 {@link #extract(Path, String)} 抽取"结构"互补。
     */
    public String extractText(Path file, String sourceType) {
        if (sourceType == null) sourceType = "unknown";
        return switch (sourceType.toLowerCase()) {
            case "docx", "doc" -> textDocx(file);
            case "xlsx", "xls" -> textXlsx(file);
            case "pdf" -> textPdf(file);
            default -> "";
        };
    }

    // ===================== docx =====================

    private TemplateStyle extractDocx(Path file) {
        TemplateStyle style = new TemplateStyle();
        style.setFormat("docx");
        try (FileInputStream fis = new FileInputStream(file.toFile());
             XWPFDocument doc = new XWPFDocument(fis)) {
            for (IBodyElement el : doc.getBodyElements()) {
                if (el instanceof XWPFParagraph p) {
                    String text = p.getText() == null ? "" : p.getText().trim();
                    if (text.isEmpty()) continue;
                    String styleId = p.getStyle();
                    if (isHeading(styleId) || looksLikeHeading(text)) {
                        style.getSections().add(text);
                    } else if (text.contains("：") || text.contains(":")) {
                        // "姓名：" 这类标签当字段
                        String label = text.split("[:：]", 2)[0].trim();
                        if (!label.isEmpty() && label.length() <= MAX_HEADING_LEN) {
                            style.getFields().add(new TemplateStyle.Field(label, "text"));
                        }
                    }
                } else if (el instanceof XWPFTable t) {
                    int rows = t.getNumberOfRows();
                    int cols = rows > 0 ? t.getRow(0).getTableCells().size() : 0;
                    List<String> header = new ArrayList<>();
                    if (rows > 0) {
                        for (XWPFTableCell c : t.getRow(0).getTableCells()) {
                            String h = c.getText() == null ? "" : c.getText().trim();
                            header.add(h);
                            if (!h.isEmpty()) style.getFields().add(new TemplateStyle.Field(h, "text"));
                        }
                    }
                    style.getTables().add(new TemplateStyle.Table(rows, cols, header));
                }
            }
        } catch (Exception e) {
            log.warn("[TemplateStyle] docx 结构抽取失败 {}: {}", file, e.getMessage());
        }
        style.inferLayoutType();
        return style;
    }

    private boolean isHeading(String styleId) {
        if (styleId == null) return false;
        String s = styleId.toLowerCase();
        return s.contains("heading") || s.contains("标题") || s.matches(".*h[1-6].*");
    }

    private boolean looksLikeHeading(String text) {
        if (text.length() > MAX_HEADING_LEN) return false;
        if (text.endsWith("。") || text.endsWith("，") || text.endsWith(",")) return false;
        return NUMBERED_HEADING.matcher(text).matches();
    }

    // ===================== xlsx =====================

    private TemplateStyle extractXlsx(Path file) {
        TemplateStyle style = new TemplateStyle();
        style.setFormat("xlsx");
        try (FileInputStream fis = new FileInputStream(file.toFile());
             XSSFWorkbook wb = new XSSFWorkbook(fis)) {
            style.getExtras().put("sheets", wb.getNumberOfSheets());
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                XSSFSheet sheet = wb.getSheetAt(i);
                if (sheet == null) continue;
                style.getSections().add(sheet.getSheetName());
                int rows = sheet.getPhysicalNumberOfRows();
                int cols = 0;
                int firstRowIdx = sheet.getFirstRowNum();
                XSSFRow first = firstRowIdx < 0 ? null : sheet.getRow(firstRowIdx);
                List<String> header = new ArrayList<>();
                if (first != null) {
                    cols = first.getPhysicalNumberOfCells();
                    for (Cell c : first) {
                        String h = c.toString() == null ? "" : c.toString().trim();
                        header.add(h);
                        if (!h.isEmpty()) style.getFields().add(new TemplateStyle.Field(h, "text"));
                    }
                }
                style.getTables().add(new TemplateStyle.Table(rows, cols, header));
            }
        } catch (Exception e) {
            log.warn("[TemplateStyle] xlsx 结构抽取失败 {}: {}", file, e.getMessage());
        }
        style.inferLayoutType();
        return style;
    }

    // ===================== pdf =====================

    private TemplateStyle extractPdf(Path file) {
        TemplateStyle style = new TemplateStyle();
        style.setFormat("pdf");
        try {
            String text = TikaExtractor.extract(file);
            if (text != null && !text.isBlank()) {
                int pages = countPdfPages(text);
                style.getExtras().put("pages", pages);
                for (String rawLine : text.split("\\r?\\n")) {
                    String line = rawLine.trim();
                    if (line.isEmpty() || line.length() > MAX_HEADING_LEN) continue;
                    if (looksLikeHeading(line)) {
                        style.getSections().add(line);
                    }
                }
            }
            // PDF 表单域（AcroForm）是最可靠的"字段"来源
            collectPdfFormFields(file, style);
        } catch (Exception e) {
            log.warn("[TemplateStyle] pdf 结构抽取失败 {}: {}", file, e.getMessage());
        }
        style.inferLayoutType();
        return style;
    }

    private int countPdfPages(String text) {
        int n = 0;
        int idx = 0;
        while ((idx = text.indexOf("--- Page ", idx)) != -1) {
            n++;
            idx += 9;
        }
        return n;
    }

    private void collectPdfFormFields(Path file, TemplateStyle style) {
        // PDFBox 3.x：PDDocument.load(...) 已迁移到 org.apache.pdfbox.Loader.loadPDF(...)
        try (org.apache.pdfbox.pdmodel.PDDocument pdf =
                     org.apache.pdfbox.Loader.loadPDF(file.toFile())) {
            org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm form = pdf.getDocumentCatalog().getAcroForm();
            if (form == null) return;
            java.util.List<org.apache.pdfbox.pdmodel.interactive.form.PDField> fields = form.getFields();
            if (fields == null || fields.isEmpty()) return;
            style.getExtras().put("hasForm", true);
            for (org.apache.pdfbox.pdmodel.interactive.form.PDField f : fields) {
                String name = f.getFullyQualifiedName();
                if (name != null && !name.isBlank()) {
                    style.getFields().add(new TemplateStyle.Field(name, "form"));
                }
            }
        } catch (Throwable t) {
            log.debug("[TemplateStyle] PDF 表单域抽取跳过（PDFBox 不可用或文件不支持）: {}", t.getMessage());
        }
    }

    // ===================== 文本抽取（预览用） =====================

    private String textDocx(Path file) {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file.toFile());
             XWPFDocument doc = new XWPFDocument(fis)) {
            for (IBodyElement el : doc.getBodyElements()) {
                if (el instanceof XWPFParagraph p) {
                    String t = p.getText();
                    if (t != null && !t.isBlank()) sb.append(t).append("\n");
                } else if (el instanceof XWPFTable t) {
                    for (XWPFTableRow r : t.getRows()) {
                        List<String> cells = new ArrayList<>();
                        for (XWPFTableCell c : r.getTableCells()) {
                            cells.add(c.getText() == null ? "" : c.getText().trim());
                        }
                        sb.append(String.join(" | ", cells)).append("\n");
                    }
                    sb.append("\n");
                }
            }
        } catch (Exception e) {
            log.warn("[TemplateStyle] docx 文本抽取失败 {}: {}", file, e.getMessage());
        }
        return sb.toString();
    }

    private String textXlsx(Path file) {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file.toFile());
             XSSFWorkbook wb = new XSSFWorkbook(fis)) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                XSSFSheet sheet = wb.getSheetAt(i);
                if (sheet == null) continue;
                sb.append("【").append(sheet.getSheetName()).append("】\n");
                for (Row r : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell c : r) cells.add(c.toString().trim());
                    sb.append(String.join("\t", cells)).append("\n");
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            log.warn("[TemplateStyle] xlsx 文本抽取失败 {}: {}", file, e.getMessage());
        }
        return sb.toString();
    }

    private String textPdf(Path file) {
        try {
            return TikaExtractor.extract(file);
        } catch (Exception e) {
            log.warn("[TemplateStyle] pdf 文本抽取失败 {}: {}", file, e.getMessage());
            return "";
        }
    }
}
