package vip.mate.tool.document;

/**
 * 修改人:祝奔；时间：2026-11-18T11:29:06.231Z；修改原因/内容：新增图片落盘回调接口，解耦文档提取器与 Wiki 图片存储服务；代码来源：AI生成
 *
 * <p>文档提取器在解析到内嵌图片时调用 {@link #store}，把字节交给上层（Wiki 摄取链路）落盘，
 * 上层返回写入正文的引用 URL（形如 {@code /wiki-images/{id}.{ext}}）。无 Wiki 上下文时上层可传入
 * 一个返回 null 的实现，提取器据此跳过图片内联。
 */
@FunctionalInterface
public interface ImageSink {

    /**
     * @param bytes      图片原始字节
     * @param mimeType   图片 MIME（如 image/png）
     * @param sourceRef  素材内的来源标识（如 excel:Sheet1!B3 / word:img3 / pdf:P2#1），用于去重
     * @return 写入正文的图片引用 URL；返回 null 表示跳过该图片
     */
    String store(byte[] bytes, String mimeType, String sourceRef);
}
