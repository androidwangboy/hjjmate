package vip.mate.tool.document;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 修改人:祝奔；时间：2026-11-18T11:29:06.231Z；修改原因/内容：新增 Office（Word/Excel）图文交错提取器：按文档流顺序把内嵌图片引用插到
 * 其所在段落/锚定单元格旁，使切片时图片只跟随相关内容；Excel 用 POI ClientAnchor 精确定位到单元格；代码来源：AI生成
 *
 * <p>解析失败返回 {@code null}，让调用方回退到既有的 pandoc / Tika 文本提取链；任何单张图片处理失败都不影响正文。
 */
@Slf4j
@Component
public class OfficeImageExtractor {

    private static final int MAX_IMAGES = 100;

    /**
     * Word：按 body 元素顺序遍历段落/表格，在包含图片的 run 之后就地插入图片引用。
     *
     * @return 带内联图片引用的文本；无图片或解析失败时返回 null（由调用方回退）
     */
    public String extractDocx(Path path, ImageSink sink) {
        if (sink == null) return null;
        int[] counter = {0};
        StringBuilder out = new StringBuilder();
        try (InputStream is = Files.newInputStream(path);
             XWPFDocument doc = new XWPFDocument(is)) {
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendParagraph(paragraph, out, sink, counter, null);
                } else if (element instanceof XWPFTable table) {
                    appendTable(table, out, sink, counter);
                }
                if (counter[0] >= MAX_IMAGES) break;
            }
        } catch (Exception e) {
            log.warn("[OfficeImage] docx parse failed for {}: {}", path.getFileName(), e.toString());
            return null;
        }
        return counter[0] > 0 ? out.toString() : null;
    }

    private void appendTable(XWPFTable table, StringBuilder out, ImageSink sink, int[] counter) {
        out.append('\n');
        for (XWPFTableRow row : table.getRows()) {
            List<String> cellTexts = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                StringBuilder cellBuf = new StringBuilder();
                for (XWPFParagraph p : cell.getParagraphs()) {
                    appendParagraph(p, cellBuf, sink, counter, "table");
                }
                cellTexts.add(cellBuf.toString().trim().replaceAll("\\s+", " "));
            }
            out.append(String.join(" | ", cellTexts)).append('\n');
            if (counter[0] >= MAX_IMAGES) break;
        }
        out.append('\n');
    }

    private void appendParagraph(XWPFParagraph paragraph, StringBuilder out, ImageSink sink,
                                 int[] counter, String sourceKind) {
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.text();
            if (text != null && !text.isBlank()) {
                out.append(text);
            }
            List<XWPFPicture> pictures = run.getEmbeddedPictures();
            for (int i = 0; i < pictures.size(); i++) {
                if (counter[0] >= MAX_IMAGES) break;
                XWPFPictureData data = pictures.get(i).getPictureData();
                byte[] bytes = data.getData();
                // XWPFPictureData 无 getMimeType()，用 PictureType 枚举拿 MIME
                String mime = data.getPictureTypeEnum().getContentType();
                String ref = "word:" + (sourceKind != null ? sourceKind + ":" : "") + (counter[0] + 1);
                String url = sink.store(bytes, mime, ref);
                if (url != null) {
                    String alt = mime != null && mime.contains("jpeg") ? "照片" : "图";
                    out.append("\n![").append(alt).append("](").append(url).append(")\n");
                    counter[0]++;
                }
            }
        }
        out.append('\n');
    }

    /**
     * Excel：用 POI 逐 sheet / 行 / 单元格输出文本；用 XSSFDrawing 的图片锚点（ClientAnchor）
     * 把图片精确插到其锚定的行、列位置。
     *
     * @return 带内联图片引用的文本；无图片或解析失败时返回 null（由调用方回退）
     */
    public String extractXlsx(Path path, ImageSink sink) {
        if (sink == null) return null;
        int[] counter = {0};
        StringBuilder out = new StringBuilder();
        DataFormatter formatter = new DataFormatter();
        // 修改人:祝奔；时间：2026-11-18T12:41:09.442Z；修改原因/内容：直接解析 OOXML 取图片锚点（兼容浮动图与嵌入单元格图，
        // 且不依赖 POI 版本的 getCellPictures/getDrawingPatriarch），按 sheet 名归集；代码来源：AI生成
        Map<String, Map<Integer, Map<Integer, List<String>>>> imagesBySheet = new HashMap<>();
        // 修改人:祝奔；时间：2026-11-18T12:58:30.117Z；修改原因/内容：结合 WPS DISPIMG 测试类，
        // 解析 xl/cellimages.xml 建立 imageId -> media 路径映射，用于嵌入单元格图片；代码来源：AI生成
        Map<String, String> wpsImageIdToPath = new HashMap<>();
        // 修改人:祝奔；时间：2026-11-18T13:10:22.000Z；修改原因/内容：外层共享一个 ZipFile，供 OOXML 图片锚点、
        // WPS cellimages 映射与图片字节读取复用，避免每张图片重复打开压缩包；代码来源：AI生成
        try (ZipFile zip = new ZipFile(path.toFile());
             InputStream is = Files.newInputStream(path);
             XSSFWorkbook wb = new XSSFWorkbook(is)) {

            imagesBySheet = collectXlsxAnchors(zip, sink, counter);
            wpsImageIdToPath = parseWpsCellImages(zip);
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                out.append("# ").append(sheet.getSheetName()).append("\n\n");

                Map<Integer, Map<Integer, List<String>>> anchors =
                        imagesBySheet.getOrDefault(sheet.getSheetName(), Map.of());

                int lastRow = Math.max(sheet.getLastRowNum(),
                        anchors.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1));
                for (int r = 0; r <= lastRow; r++) {
                    Row row = sheet.getRow(r);
                    StringBuilder line = new StringBuilder();
                    int lastCol = row == null ? -1 : row.getLastCellNum();
                    Map<Integer, List<String>> byCol = anchors.get(r);
                    for (int c = 0; c <= lastCol; c++) {
                        String value = "";
                        List<String> wpsImageUrls = new ArrayList<>();
                        if (row != null) {
                            Cell cell = row.getCell(c);
                            if (cell != null && cell.getCellType() != CellType.BLANK) {
                                // 修改人:祝奔；时间：2026-11-18T12:58:30.117Z；修改原因/内容：WPS 嵌入单元格图片是 =DISPIMG("ID_xxx",1) 公式，
                                // 按公式里的 imageId 落盘图片并就地内联；同时抑制公式文本显示；代码来源：AI生成
                                String dispimgId = extractWpsDispimgId(cell);
                                boolean isImageCell = false;
                                if (dispimgId != null && counter[0] < MAX_IMAGES) {
                                    String mediaPath = wpsImageIdToPath.get(dispimgId);
                                    if (mediaPath != null) {
                                        String url = storeWpsImage(zip, mediaPath, sheet.getSheetName(),
                                                r, c, dispimgId, sink);
                                        if (url != null) {
                                            wpsImageUrls.add(url);
                                            counter[0]++;
                                            isImageCell = true;
                                        }
                                    }
                                }
                                // 图片单元格不显示 =DISPIMG(...) 公式文本；普通单元格照常格式化
                                if (!isImageCell && dispimgId == null) {
                                    value = formatter.formatCellValue(cell).trim();
                                }
                            }
                        }
                        line.append(value);
                        // 图片插到锚定单元格之后（图文交错，切片时跟随该行）
                        if (byCol != null) {
                            List<String> urls = byCol.get(c);
                            if (urls != null) {
                                for (String url : urls) line.append(" ![图](").append(url).append(")");
                            }
                        }
                        for (String url : wpsImageUrls) line.append(" ![图](").append(url).append(")");
                        line.append(" | ");
                    }
                    // 锚定列超出实际单元格范围（或空行）的图片集中追加到本行末尾，保证不丢失
                    if (byCol != null) {
                        for (Map.Entry<Integer, List<String>> e : byCol.entrySet()) {
                            if (e.getKey() < 0 || e.getKey() > lastCol) {
                                for (String url : e.getValue()) line.append(" ![图](").append(url).append(")");
                            }
                        }
                    }
                    if (!line.toString().isBlank()) {
                        out.append(line.toString().replaceAll("\\s+\\|", " |")).append('\n');
                    }
                }
                out.append('\n');
            }
        } catch (Exception e) {
            log.warn("[OfficeImage] xlsx parse failed for {}: {}", path.getFileName(), e.toString());
            return null;
        }
        boolean hasAnyImage = imagesBySheet.values().stream()
                .anyMatch(m -> m.values().stream().anyMatch(c -> !c.isEmpty()))
                || !wpsImageIdToPath.isEmpty();
        return hasAnyImage ? out.toString() : null;
    }

    /**
     * 修改人:祝奔；时间：2026-11-18T12:41:09.442Z；修改原因/内容：直接解析 XLSX 的 OOXML（ZIP+DOM）提取图片及单元格锚点，
     * 兼容浮动图片与嵌入单元格图片，且不依赖 POI 的 getCellPictures()/getDrawingPatriarch()；代码来源：AI生成
     *
     * <p>链路：workbook.xml（sheet 名 + r:id）→ workbook.xml.rels（rId → sheetN.xml）→
     * sheetN.xml.rels（rId → drawingM.xml）→ drawingM.xml（anchor 的 row/col + blip r:embed）→
     * drawingM.xml.rels（rId → ../media/imageX）→ 读取字节交给 sink。
     *
     * @return key=sheet 名，value= row → col → 图片引用 URL 列表
     */
    public static void main(String[] args) {
        OfficeImageExtractor officeImageExtractor = new OfficeImageExtractor();
        File file = new File("C:\\Users\\lrl.DESKTOP-KUBA0H8\\Downloads\\系统bug-修改代码.xlsx");
        int[] counter = {0};
        officeImageExtractor.collectXlsxAnchors(Path.of(file.toURI()), null, counter);
    }

    /**
     * 修改人:祝奔；时间：2026-11-18T12:58:30.117Z；修改原因/内容：解析 WPS「嵌入单元格图片」的两张映射表：
     * xl/cellimages.xml（xdr:cNvPr@name=imageId → a:blip@r:embed=rId）与
     * xl/_rels/cellimages.xml.rels（rId → media/imageX）。返回 imageId -> zip 内图片路径；代码来源：AI生成
     */
    private Map<String, String> parseWpsCellImages(ZipFile zip) {
        Map<String, String> imageIdToPath = new HashMap<>();
        try {
            if (zip.getEntry("xl/cellimages.xml") == null) {
                return imageIdToPath;
            }
            Document cellImages = readXml(zip, "xl/cellimages.xml");
            if (cellImages == null) return imageIdToPath;

            Map<String, String> imageIdToRid = new HashMap<>();
            NodeList pics = cellImages.getElementsByTagNameNS("*", "pic");
            for (int i = 0; i < pics.getLength(); i++) {
                Element pic = (Element) pics.item(i);
                NodeList cNvPrList = pic.getElementsByTagNameNS("*", "cNvPr");
                NodeList blipList = pic.getElementsByTagNameNS("*", "blip");
                if (cNvPrList.getLength() == 0 || blipList.getLength() == 0) continue;
                String imageId = ((Element) cNvPrList.item(0)).getAttribute("name");
                String rid = attrAnyNs((Element) blipList.item(0), "embed");
                if (imageId != null && !imageId.isEmpty() && rid != null && !rid.isEmpty()) {
                    imageIdToRid.put(imageId, rid);
                }
            }

            Map<String, String> ridToTarget = readRels(zip, "xl/_rels/cellimages.xml.rels");
            for (Map.Entry<String, String> e : imageIdToRid.entrySet()) {
                String target = ridToTarget.get(e.getValue());
                if (target == null) continue;
                String mediaPath = normalizeZipPath("xl/", target);
                if (zip.getEntry(mediaPath) != null) {
                    imageIdToPath.put(e.getKey(), mediaPath);
                }
            }
        } catch (Exception e) {
            log.debug("[OfficeImage] WPS cellimages parse failed: {}", e.getMessage());
        }
        return imageIdToPath;
    }

    /** 从单元格公式 DISPIMG("ID_xxx", 1) 中提取 imageId；非 DISPIMG 公式返回 null。 */
    private String extractWpsDispimgId(Cell cell) {
        if (cell.getCellType() != CellType.FORMULA) return null;
        String formula = cell.getCellFormula();
        if (formula == null || !formula.toUpperCase(java.util.Locale.ROOT).contains("DISPIMG")) return null;
        int start = formula.indexOf('"');
        if (start < 0) return null;
        int end = formula.indexOf('"', start + 1);
        if (end < 0) return null;
        String id = formula.substring(start + 1, end);
        return id.isEmpty() ? null : id;
    }

    /** 修改人:祝奔；时间：2026-11-18T13:10:22.000Z；修改原因/内容：改用外层共享 ZipFile 读取图片字节，避免重复打开压缩包；代码来源：AI生成 */
    private String storeWpsImage(ZipFile zip, String mediaPath, String sheetName,
                                 int row, int col, String imageId, ImageSink sink) {
        try {
            byte[] bytes = readZipBytes(zip, mediaPath);
            if (bytes == null || bytes.length == 0) return null;
            String mime = mimeOfPath(mediaPath);
            String ref = "excel-wps:" + sheetName + "!" + (row + 1) + ":" + (col + 1)
                    + "#" + imageId;
            return sink.store(bytes, mime, ref);
        } catch (Exception e) {
            log.warn("[OfficeImage] WPS image store failed for {} at {}:{}: {}",
                    mediaPath, row, col, e.getMessage());
            return null;
        }
    }

    /** 修改人:祝奔；时间：2026-11-18T13:10:22.000Z；修改原因/内容：保留 Path 重载供测试 main 使用，内部打开共享 ZipFile 后委托；代码来源：AI生成 */
    private Map<String, Map<Integer, Map<Integer, List<String>>>> collectXlsxAnchors(
            Path path, ImageSink sink, int[] counter) {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            return collectXlsxAnchors(zip, sink, counter);
        } catch (Exception e) {
            log.warn("[OfficeImage] xlsx OOXML image parse failed for {}: {}", path.getFileName(), e.toString());
            return new HashMap<>();
        }
    }

    private Map<String, Map<Integer, Map<Integer, List<String>>>> collectXlsxAnchors(
            ZipFile zip, ImageSink sink, int[] counter) {
        Map<String, Map<Integer, Map<Integer, List<String>>>> bySheet = new HashMap<>();
        try {
            // workbook.xml.rels: rId -> sheet/drawing 目标路径
            Map<String, String> workbookRels = readRels(zip, "xl/_rels/workbook.xml.rels");
            Document workbook = readXml(zip, "xl/workbook.xml");
            if (workbook == null) return bySheet;

            NodeList sheets = workbook.getElementsByTagNameNS("*", "sheet");
            for (int i = 0; i < sheets.getLength(); i++) {
                if (counter[0] >= MAX_IMAGES) break;
                Element sheetEl = (Element) sheets.item(i);
                String sheetName = sheetEl.getAttribute("name");
                String rid = attrAnyNs(sheetEl, "id"); // r:id
                String sheetTarget = workbookRels.get(rid);
                if (sheetTarget == null) continue;
                String sheetPath = normalizeZipPath("xl/", sheetTarget);

                // 该 sheet 的 rels：找 drawing
                String sheetRelsPath = relsPathFor(sheetPath);
                Map<String, String> sheetRels = readRels(zip, sheetRelsPath);
                for (Map.Entry<String, String> rel : sheetRels.entrySet()) {
                    if (counter[0] >= MAX_IMAGES) break;
                    String target = rel.getValue();
                    if (target == null || !target.contains("drawing")) continue;
                    String drawingPath = normalizeZipPath(parentPath(sheetPath), target);
                    parseDrawing(zip, drawingPath, sheetName, bySheet, sink, counter);
                }
            }
        } catch (Exception e) {
            log.warn("[OfficeImage] xlsx OOXML image parse failed: {}", e.toString());
        }
        return bySheet;
    }

    /** 解析一个 drawingN.xml，提取所有图片锚点。 */
    private void parseDrawing(ZipFile zip, String drawingPath, String sheetName,
                              Map<String, Map<Integer, Map<Integer, List<String>>>> bySheet,
                              ImageSink sink, int[] counter) {
        Document drawing = readXml(zip, drawingPath);
        if (drawing == null) return;

        // drawing rels: rId -> 图片路径
        Map<String, String> drawingRels = readRels(zip, relsPathFor(drawingPath));

        // 所有锚点元素（twoCellAnchor/oneCellAnchor/absoluteAnchor 统一处理）
        // 修改人:祝奔；时间：2026-11-18T12:41:09.442Z；修改原因/内容：常规 xlsx 图片均在 twoCellAnchor/oneCellAnchor 内，
        // 覆盖这两类即可拿到 from 坐标；去掉对独立 pic 的兜底以免重复计数；代码来源：AI生成
        NodeList anchors = drawing.getElementsByTagNameNS("*", "twoCellAnchor");
        appendAnchors(anchors, zip, drawingRels, drawingPath, sheetName, bySheet, sink, counter);
        anchors = drawing.getElementsByTagNameNS("*", "oneCellAnchor");
        appendAnchors(anchors, zip, drawingRels, drawingPath, sheetName, bySheet, sink, counter);
    }

    private void appendAnchors(NodeList anchors, ZipFile zip, Map<String, String> drawingRels,
                               String drawingPath, String sheetName,
                               Map<String, Map<Integer, Map<Integer, List<String>>>> bySheet,
                               ImageSink sink, int[] counter) {
        for (int i = 0; i < anchors.getLength(); i++) {
            if (counter[0] >= MAX_IMAGES) break;
            Element anchor = (Element) anchors.item(i);
            // 锚点起始单元格：取 <xdr:from> 下的 col / row（0-based）
            int row = -1, col = -1;
            NodeList fromList = anchor.getElementsByTagNameNS("*", "from");
            if (fromList.getLength() > 0) {
                Element from = (Element) fromList.item(0);
                col = firstInt(from, "col", -1);
                row = firstInt(from, "row", -1);
            }
            // 图片 rId：<a:blip r:embed="rIdN">
            NodeList blips = anchor.getElementsByTagNameNS("*", "blip");
            for (int b = 0; b < blips.getLength(); b++) {
                if (counter[0] >= MAX_IMAGES) break;
                Element blip = (Element) blips.item(b);
                String embedRid = attrAnyNs(blip, "embed");
                if (embedRid == null) continue;
                String imgTarget = drawingRels.get(embedRid);
                if (imgTarget == null) continue;
                String imgPath = normalizeZipPath(parentPath(drawingPath), imgTarget);
                byte[] bytes = readZipBytes(zip, imgPath);
                if (bytes == null || bytes.length == 0) continue;
                String mime = mimeOfPath(imgPath);
                int effRow = Math.max(row, 0);
                int effCol = col < 0 || col > 200 ? -1 : col;
                String ref = "excel:" + sheetName + "!" + (effRow + 1) + ":" + (effCol + 1)
                        + "#ooxml" + (counter[0] + 1);
                String url = sink.store(bytes, mime, ref);
                if (url == null) continue;
                bySheet.computeIfAbsent(sheetName, k -> new HashMap<>())
                        .computeIfAbsent(effRow, k -> new HashMap<>())
                        .computeIfAbsent(effCol, k -> new ArrayList<>())
                        .add(url);
                counter[0]++;
            }
        }
    }

    // ---------- OOXML 基础工具 ----------

    private Document readXml(ZipFile zip, String path) {
        byte[] bytes = readZipBytes(zip, path);
        if (bytes == null) return null;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            log.debug("[OfficeImage] XML parse failed for {}: {}", path, e.getMessage());
            return null;
        }
    }

    /** 读取 *.rels，返回 rId -> Target（去掉 TargetMode="External"）。 */
    private Map<String, String> readRels(ZipFile zip, String relsPath) {
        Map<String, String> result = new HashMap<>();
        Document doc = readXml(zip, relsPath);
        if (doc == null) return result;
        NodeList rels = doc.getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < rels.getLength(); i++) {
            Element rel = (Element) rels.item(i);
            String id = rel.getAttribute("Id");
            String target = rel.getAttribute("Target");
            String mode = rel.getAttribute("TargetMode");
            if (id != null && target != null && !"External".equalsIgnoreCase(mode)) {
                result.put(id, target);
            }
        }
        return result;
    }

    private byte[] readZipBytes(ZipFile zip, String path) {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) return null;
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        } catch (Exception e) {
            log.debug("[OfficeImage] read zip entry {} failed: {}", path, e.getMessage());
            return null;
        }
    }

    /** 把 rels 里的相对 Target 归一化为 zip 内绝对路径。 */
    private String normalizeZipPath(String baseDir, String target) {
        String t = target.replace('\\', '/');
        if (t.startsWith("/")) return t.substring(1); // 绝对包内路径
        String base = baseDir.endsWith("/") ? baseDir : baseDir + "/";
        java.util.ArrayDeque<String> parts = new java.util.ArrayDeque<>();
        for (String p : (base + t).split("/")) {
            if (p.isEmpty() || p.equals(".")) continue;
            if (p.equals("..")) { parts.pollLast(); continue; }
            parts.addLast(p);
        }
        return String.join("/", parts);
    }

    private String parentPath(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private String relsPathFor(String partPath) {
        String parent = parentPath(partPath);
        String name = partPath.substring(partPath.lastIndexOf('/') + 1);
        return (parent.isEmpty() ? "" : parent + "/") + "_rels/" + name + ".rels";
    }

    private String attrAnyNs(Element el, String localName) {
        String v = el.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", localName);
        if (v == null || v.isEmpty()) v = el.getAttribute(localName);
        return (v == null || v.isEmpty()) ? null : v;
    }

    private int firstInt(Element parent, String localName, int def) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) return def;
        try {
            return Integer.parseInt(nodes.item(0).getTextContent().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private String mimeOfPath(String path) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

}
