package vip.mate.tool.builtin;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.XmlUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class EmbeddedImageExtractor {

    // 用于缓存 图片ID -> rId 的映射
    private static Map<String, String> idToRidMap = new HashMap<>();
    // 用于缓存 rId -> 图片文件路径 (在 zip 中的路径，如 "media/image1.png")
    private static Map<String, String> ridToTargetMap = new HashMap<>();

    /**
     * 主方法：提取指定工作表中嵌入单元格的图片
     *
     * @param excelFilePath Excel 文件路径
     * @param sheetIndex    工作表索引 (从0开始)
     * @param outputDir     图片输出目录
     */
    public static void extractEmbeddedCellImages(String excelFilePath, int sheetIndex, String outputDir) {
        File outputFolder = new File(outputDir);
        if (!outputFolder.exists()) {
            outputFolder.mkdirs();
        }

        try (FileInputStream fis = new FileInputStream(excelFilePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

            // 步骤 1: 解析 Excel 的 XML，建立映射关系 (必须在读取图片数据前完成)
            parseCellImagesXml(excelFilePath);
            System.out.println("图片ID->rId映射: " + idToRidMap);
            System.out.println("rId->文件路径映射: " + ridToTargetMap);

            // 步骤 2: 获取工作簿中所有的图片数据 (以 rId 的数值部分作为标识，但这里我们通过路径匹配)
            // getAllPictures() 返回所有图片，但我们需要根据 Target 路径来识别。
            // 实际上，XSSFPictureData 的包部件名称包含了图片路径，可以用来匹配。

            Sheet sheet = workbook.getSheetAt(sheetIndex);
            DataFormatter formatter = new DataFormatter();

            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.FORMULA) {
                        String formula = cell.getCellFormula();
                        // 判断是否为 WPS 的 DISPIMG 嵌入图片公式
                        if (formula != null && formula.contains("DISPIMG")) {
                            String imageId = extractImageIdFromFormula(formula);
                            if (imageId != null) {
                                System.out.println("单元格 " + cell.getAddress() + " 发现嵌入图片，ID: " + imageId);
                                saveImageByImageId(workbook, imageId, outputDir);
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 从公式 =DISPIMG("ID_...", 1) 中提取图片 ID
     */
    private static String extractImageIdFromFormula(String formula) {
        // 简单解析: 查找 "ID_" 开头，并以引号结束的部分
        int start = formula.indexOf('"');
        if (start == -1) return null;
        int end = formula.indexOf('"', start + 1);
        if (end == -1) return null;
        return formula.substring(start + 1, end);
    }

    /**
     * 解析 xlsx 包内的 XML 文件，建立映射关系
     */
    private static void parseCellImagesXml(String excelFilePath) throws IOException {
        idToRidMap.clear();
        ridToTargetMap.clear();

        try (ZipFile zipFile = new ZipFile(excelFilePath)) {
            // 1. 解析 xl/cellimages.xml: 获取 <xdr:cNvPr name="ID_..."> 和 <a:blip r:embed="rIdX"/>
            ZipEntry cellImagesEntry = zipFile.getEntry("xl/cellimages.xml");
            if (cellImagesEntry == null) {
                System.out.println("未找到 xl/cellimages.xml，可能不是 WPS 嵌入图片格式。");
                return;
            }

            try (InputStream is = zipFile.getInputStream(cellImagesEntry)) {
                Document doc = XmlUtil.readXML(is);
                NodeList picNodes = doc.getElementsByTagName("xdr:pic");
                for (int i = 0; i < picNodes.getLength(); i++) {
                    Element pic = (Element) picNodes.item(i);
                    // 获取图片 ID (name 属性)
                    NodeList cNvPrList = pic.getElementsByTagName("xdr:cNvPr");
                    if (cNvPrList.getLength() > 0) {
                        String imageId = ((Element) cNvPrList.item(0)).getAttribute("name");
                        // 获取 rId (r:embed 属性)
                        NodeList blipList = pic.getElementsByTagName("a:blip");
                        if (blipList.getLength() > 0) {
                            String rId = ((Element) blipList.item(0)).getAttribute("r:embed");
                            if (imageId != null && rId != null) {
                                idToRidMap.put(imageId, rId);
                            }
                        }
                    }
                }
            }

            // 2. 解析 xl/_rels/cellimages.xml.rels: 获取 <Relationship Id="rIdX" Target="media/imageY.png"/>
            ZipEntry relsEntry = zipFile.getEntry("xl/_rels/cellimages.xml.rels");
            if (relsEntry == null) {
                System.out.println("未找到 xl/_rels/cellimages.xml.rels。");
                return;
            }

            try (InputStream is = zipFile.getInputStream(relsEntry)) {
                Document doc = XmlUtil.readXML(is);
                NodeList relNodes = doc.getElementsByTagName("Relationship");
                for (int i = 0; i < relNodes.getLength(); i++) {
                    Element rel = (Element) relNodes.item(i);
                    String rId = rel.getAttribute("Id");
                    String target = rel.getAttribute("Target");
                    if (rId != null && target != null) {
                        // Target 可能是 "media/image1.png" 或 "/xl/media/image1.png"，统一处理
                        if (target.startsWith("/")) {
                            target = target.substring(1);
                        }
                        if (!target.startsWith("xl/")) {
                            target = "xl/" + target;
                        }
                        ridToTargetMap.put(rId, target);
                    }
                }
            }
        }
    }

    /**
     * 根据图片 ID 查找并保存图片
     */
    private static void saveImageByImageId(XSSFWorkbook workbook, String imageId, String outputDir) throws IOException {
        // 1. 通过 imageId 找到 rId
        String rId = idToRidMap.get(imageId);
        if (rId == null) {
            System.out.println("  -> 未找到该图片ID对应的rId，可能图片已被删除或格式异常。");
            return;
        }

        // 2. 通过 rId 找到在 zip 包中的路径
        String targetPath = ridToTargetMap.get(rId);
        if (targetPath == null) {
            System.out.println("  -> 未找到rId对应的图片路径。");
            return;
        }

        // 3. 遍历 workbook 中的所有图片，找到包部件路径与 targetPath 匹配的那一个
        //    XSSFPictureData 的 getPackagePart().getPartName().getName() 返回类似 "/xl/media/image1.png"
        List<XSSFPictureData> pictures = workbook.getAllPictures();
        for (XSSFPictureData pictureData : pictures) {
            String partName = pictureData.getPackagePart().getPartName().getName(); // 例如 "/xl/media/image1.png"
            // 规范化路径以便比较
            String normalizedPartName = partName.startsWith("/") ? partName.substring(1) : partName;

            if (normalizedPartName.equals(targetPath)) {
                // 找到匹配的图片，保存到本地
                String fileName = FileUtil.getName(targetPath); // 例如 "image1.png"
                File outputFile = new File(outputDir, fileName);
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    fos.write(pictureData.getData());
                }
                System.out.println("  -> 图片已保存至: " + outputFile.getAbsolutePath());
                return;
            }
        }
        System.out.println("  -> 在 workbook 的图片列表中未找到路径为 " + targetPath + " 的图片。");
    }

    // 测试入口
    public static void main(String[] args) {
        // 替换为你的实际文件路径和输出目录
        String excelPath = "C:\\Users\\lrl.DESKTOP-KUBA0H8\\Downloads\\系统bug-修改代码.xlsx";
        String outputDirectory = "C:\\path\\to\\output_images";
        extractEmbeddedCellImages(excelPath, 0, outputDirectory);
    }
}