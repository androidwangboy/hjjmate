package vip.mate.wiki.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import vip.mate.tool.image.vision.ImageVisionService;
import vip.mate.tool.image.vision.VisionRequest;
import vip.mate.tool.image.vision.VisionResult;
// 修改人:祝奔；时间：2026-11-18T09:27:23.022Z；修改原因/内容：引入图片存储服务，使 PDF 内嵌图落盘并生成鉴权 URL；代码来源：AI生成
import vip.mate.wiki.service.WikiImageService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts inline images from a PDF and runs each through the vision-in
 * pipeline to produce a {@code [图 P{page}#{idx}]: <caption>} marker line.
 *
 * <p>Constants follow standard image-extractor conventions:
 * <ul>
 *   <li>Min image side: 100 px (filters favicon-sized logos and dividers)</li>
 *   <li>Max images per PDF: 50 (vision API cost guard; tunable in a follow-up
 *       once production cost data is in)</li>
 * </ul>
 *
 * <p>Output is appended to the compile pipeline's main text body so chunks
 * become searchable by their image content (e.g. searching "营收" finds
 * pages with revenue charts even if the figure caption is below the chart).
 *
 * @author MateClaw Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfImageExtractor {

    private static final int MIN_IMAGE_SIDE_PX = 100;
    private static final int MAX_IMAGES_PER_PDF = 50;

    private final ImageVisionService imageVisionService;

    /**
     * 修改人:祝奔；时间：2026-11-18T09:27:23.022Z；修改原因/内容：可选注入图片存储服务，直接 new 的测试场景为 null 时仅生成 caption 不落盘；代码来源：AI生成
     */
    @Autowired(required = false)
    @Nullable
    private WikiImageService wikiImageService;

    /**
     * Optional dependency: when {@link #captionInlineImages(Path)} is called
     * but no vision service is wired (test harness, vision module excluded),
     * the extractor returns an empty list rather than NPE-ing.
     */
    @Autowired(required = false)
    private void noopWhenVisionAbsent() {
        // Marker method; the @RequiredArgsConstructor field is required at
        // runtime in production. Tests construct the extractor directly with
        // a mock or a no-op wrapper.
    }

    /**
     * Walks every page of {@code pdfPath}, captions each qualifying inline
     * image, and returns the rendered marker lines in document order.
     *
     * <p>Returns an empty list when the PDF can't be opened, contains no
     * inline images, or all images fall below the size threshold. Per-image
     * vision failures are logged and skipped; the rest of the document is
     * still processed.
     */
    public List<String> captionInlineImages(Path pdfPath) {
        return captionInlineImages(pdfPath, null, null, null, true);
    }

    /**
     * 修改人:祝奔；时间：2026-11-18T09:27:23.022Z；修改原因/内容：新增带素材上下文的重载：PDF 内嵌图落盘为图片 URL 后再追加 caption，使页面可显示图片；
     * kbId 为 null 时退化为旧的"仅 caption"行为；代码来源：AI生成
     */
    public List<String> captionInlineImages(Path pdfPath, Long kbId, Long rawId, Long workspaceId,
                                            boolean captionEnabled) {
        if (pdfPath == null) {
            return List.of();
        }
        File pdfFile = pdfPath.toFile();
        if (!pdfFile.isFile()) {
            log.warn("[PdfImage] PDF path is not a regular file: {}", pdfPath);
            return List.of();
        }

        List<String> snippets = new ArrayList<>();
        int totalCaptioned = 0;

        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            int pageIdx = 0;
            for (PDPage page : doc.getPages()) {
                pageIdx++;
                if (totalCaptioned >= MAX_IMAGES_PER_PDF) {
                    log.info("[PdfImage] hit max images cap ({}) for {}; stopping early",
                            MAX_IMAGES_PER_PDF, pdfPath);
                    break;
                }

                PDResources resources = page.getResources();
                if (resources == null) {
                    continue;
                }

                int imgIdx = 0;
                for (COSName xobjectName : resources.getXObjectNames()) {
                    if (totalCaptioned >= MAX_IMAGES_PER_PDF) {
                        break;
                    }

                    PDXObject xobject;
                    try {
                        xobject = resources.getXObject(xobjectName);
                    } catch (IOException ioe) {
                        log.debug("[PdfImage] xobject load failed at page={} name={}: {}",
                                pageIdx, xobjectName.getName(), ioe.getMessage());
                        continue;
                    }
                    if (!(xobject instanceof PDImageXObject pdImage)) {
                        continue;
                    }

                    BufferedImage bufferedImage;
                    try {
                        bufferedImage = pdImage.getImage();
                    } catch (IOException | RuntimeException e) {
                        log.debug("[PdfImage] decode failed at page={} idx={}: {}",
                                pageIdx, imgIdx + 1, e.getMessage());
                        continue;
                    }
                    if (bufferedImage.getWidth() < MIN_IMAGE_SIDE_PX
                            || bufferedImage.getHeight() < MIN_IMAGE_SIDE_PX) {
                        continue;
                    }

                    imgIdx++;
                    totalCaptioned++;

                    byte[] pngBytes;
                    try {
                        pngBytes = encodeAsPng(bufferedImage);
                    } catch (IOException ioe) {
                        log.debug("[PdfImage] PNG encode failed at page={} idx={}: {}",
                                pageIdx, imgIdx, ioe.getMessage());
                        continue;
                    }

                    // 修改人:祝奔；时间：2026-11-18T09:27:23.022Z；修改原因/内容：有上下文时把 PDF 内嵌图落盘，并输出 markdown 图片引用供页面显示；代码来源：AI生成
                    String sourceRef = String.format("P%d#%d", pageIdx, imgIdx);
                    if (kbId != null && wikiImageService != null) {
                        String url = wikiImageService.store(pngBytes, "png", "image/png",
                                kbId, rawId, workspaceId, sourceRef);
                        if (url != null) {
                            snippets.add(String.format("![图 %s](%s)", sourceRef, url));
                        }
                    }

                    String caption = captionEnabled ? captionOrNull(pngBytes) : null;
                    if (caption != null && !caption.isBlank()) {
                        snippets.add(String.format("[图 %s]: %s", sourceRef, caption));
                    } else {
                        log.debug("[PdfImage] no caption (disabled or empty) page={} idx={}", pageIdx, imgIdx);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("[PdfImage] PDF load failed for {}: {}", pdfPath, e.getMessage());
        }

        log.info("[PdfImage] captioned {} image(s) from {}", snippets.size(), pdfPath);
        return snippets;
    }

    private String captionOrNull(byte[] pngBytes) {
        if (imageVisionService == null) {
            return null;
        }
        try {
            VisionResult result = imageVisionService.caption(VisionRequest.builder()
                    .imageBytes(pngBytes)
                    .mimeType("image/png")
                    .build());
            return result == null ? null : result.getCaption();
        } catch (Exception e) {
            // Non-fatal: per-image vision failures should not abort the rest of the doc.
            log.debug("[PdfImage] vision call failed: {}", e.getMessage());
            return null;
        }
    }

    private static byte[] encodeAsPng(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        }
    }
}
