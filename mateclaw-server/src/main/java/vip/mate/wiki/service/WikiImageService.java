package vip.mate.wiki.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vip.mate.wiki.model.WikiImageEntity;
import vip.mate.wiki.repository.WikiImageMapper;
import vip.mate.exception.MateClawException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 修改人:祝奔；时间：2026-11-18T09:27:23.022Z；修改原因/内容：新增 Wiki 内嵌图片存储服务：图片本体落盘、元数据入库、生成根相对引用 URL；
 * 访问鉴权由 WikiImageController 负责；代码来源：AI生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiImageService {

    /** 允许落盘的扩展名与对应 MIME，防止任意文件写入/下载。 */
    private static final Set<String> ALLOWED_EXT = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "bmp");

    /** 正文中保存的图片引用前缀（含部署 context path）。 */
    public static final String URL_PREFIX = "/wiki-images/";

    /** 鉴权访问接口前缀，供前端把图片引用换成带 JWT 的 blob URL。 */
    public static final String API_PATH_PREFIX = "/api/v1/wiki/images/";

    private final WikiImageMapper imageMapper;

    /** 图片本体存储目录。 */
    @Value("${mate.wiki.image-dir:./data/wiki-images}")
    private String imageDir;

    private Path storageDir;

    @PostConstruct
    public void init() {
        this.storageDir = Paths.get(imageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            log.warn("[WikiImage] Cannot create image dir {}: {}", storageDir, e.toString());
        }
    }

    /**
     * 保存一张内嵌图片并返回写入正文的引用 URL（形如 {@code /wiki-images/{id}.png}）。
     * 去重优先级：workspace+kb+内容 SHA-256（跨素材/跨转换复用同一张图），其次兼容旧逻辑 rawId+sourceRef。
     */
    // 修改人:祝奔；时间：2026-11-20T06:14:14.191Z；修改原因/内容：图片按内容 sha256 在 workspace+kb 范围内去重，并处理并发冲突与写盘失败脏记录；代码来源：AI生成
    public String store(byte[] bytes, String ext, String mimeType, Long kbId, Long rawId,
                        Long workspaceId, String sourceRef) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String normalizedExt = normalizeExt(ext, mimeType);
        if (normalizedExt == null) {
            log.warn("[WikiImage] Unsupported image ext={} mime={}", ext, mimeType);
            return null;
        }
        final String contentHash = sha256Hex(bytes);

        // 1) 内容 hash 去重（workspace + kb 范围内，同一张图跨素材/跨转换只存一份）
        WikiImageEntity same = imageMapper.selectOne(new LambdaQueryWrapper<WikiImageEntity>()
                .eq(WikiImageEntity::getWorkspaceId, workspaceId)
                .eq(WikiImageEntity::getKbId, kbId)
                .eq(WikiImageEntity::getContentHash, contentHash)
                .eq(WikiImageEntity::getDeleted, 0)
                .orderByDesc(WikiImageEntity::getId)
                .last("LIMIT 1"));
        if (same != null) {
            return url(same);
        }

        // 2) 兼容旧逻辑：同一素材同一来源引用（存量无 content_hash 的记录仍可命中）
        if (rawId != null && sourceRef != null && !sourceRef.isBlank()) {
            WikiImageEntity existing = imageMapper.selectOne(new LambdaQueryWrapper<WikiImageEntity>()
                    .eq(WikiImageEntity::getRawId, rawId)
                    .eq(WikiImageEntity::getSourceRef, sourceRef)
                    .eq(WikiImageEntity::getDeleted, 0)
                    .orderByDesc(WikiImageEntity::getId)
                    .last("LIMIT 1"));
            if (existing != null) {
                return url(existing);
            }
        }

        WikiImageEntity entity = new WikiImageEntity();
        entity.setKbId(kbId);
        entity.setRawId(rawId);
        entity.setWorkspaceId(workspaceId);
        entity.setExt(normalizedExt);
        entity.setMimeType(mimeType != null && !mimeType.isBlank() ? mimeType : "image/" + normalizedExt);
        entity.setFileSize((long) bytes.length);
        entity.setSourceRef(sourceRef);
        entity.setContentHash(contentHash);
        entity.setDeleted(0);
        try {
            imageMapper.insert(entity);
        } catch (DataIntegrityViolationException dup) {
            // 并发插入同一张图：唯一索引冲突后回查已存在的记录复用其 URL
            WikiImageEntity concurrent = imageMapper.selectOne(new LambdaQueryWrapper<WikiImageEntity>()
                    .eq(WikiImageEntity::getWorkspaceId, workspaceId)
                    .eq(WikiImageEntity::getKbId, kbId)
                    .eq(WikiImageEntity::getContentHash, contentHash)
                    .eq(WikiImageEntity::getDeleted, 0)
                    .orderByDesc(WikiImageEntity::getId)
                    .last("LIMIT 1"));
            if (concurrent != null) {
                log.info("[WikiImage] dedup concurrent insert, reuse id={} hash={}", concurrent.getId(), contentHash);
                return url(concurrent);
            }
            throw dup;
        }

        Path target = storageDir.resolve(kbId+"").resolve(entity.getId() + "." + normalizedExt).normalize();
        if (!target.startsWith(storageDir)) {
            imageMapper.deleteById(entity.getId());
            throw new MateClawException("err.wiki.image_path", "Illegal image storage path");
        }
        try {
            Files.createDirectories(storageDir.resolve(kbId+""));
            Files.write(target, bytes);
        } catch (IOException e) {
            // 写盘失败不留脏记录（否则下次会命中一条指向空文件的映射）
            try {
                imageMapper.deleteById(entity.getId());
            } catch (Exception cleanup) {
                log.warn("[WikiImage] failed to cleanup dirty row id={}: {}", entity.getId(), cleanup.toString());
            }
            log.error("[WikiImage] Failed to write image id={}: {}", entity.getId(), e.toString());
            throw new MateClawException("err.wiki.image_write", "Failed to store image");
        }
        log.info("[WikiImage] stored id={} raw={} bytes={} hash={} path={}",
                entity.getId(), rawId, bytes.length, contentHash, target.getFileName());
        return url(entity);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // 修改人:祝奔；时间：2026-11-20T07:23:14.892Z；修改原因/内容：新增按指定 kbId/图片ID 落盘方法，供导入还原图片复用；代码来源：AI生成
    /** 导入场景：把图片字节写到 image-dir/{kbId}/{imageId}.{ext}，返回落盘路径。 */
    public Path restoreFile(Long kbId, Long imageId, String ext, byte[] bytes) {
        String normalizedExt = normalizeExt(ext, null);
        if (normalizedExt == null) {
            throw new MateClawException("err.wiki.image_path", "Illegal image ext: " + ext);
        }
        Path dir = storageDir.resolve(String.valueOf(kbId)).normalize();
        Path target = dir.resolve(imageId + "." + normalizedExt).normalize();
        if (!target.startsWith(storageDir)) {
            throw new MateClawException("err.wiki.image_path", "Illegal image storage path");
        }
        try {
            Files.createDirectories(dir);
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new MateClawException("err.wiki.image_write", "Failed to restore image: " + e.getMessage());
        }
        return target;
    }

    /** 取图片元数据（不存在返回 empty）。 */
    public Optional<WikiImageEntity> get(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(imageMapper.selectById(id))
                .filter(e -> e.getDeleted() == null || e.getDeleted() == 0);
    }

    /** 定位图片本体文件。 */
    public Optional<Path> pathOf(WikiImageEntity entity) {
        String ext = normalizeExt(entity.getExt(), entity.getMimeType());
        if (ext == null) {
            return Optional.empty();
        }
        Path file = storageDir.resolve(entity.getKbId()+"").resolve(entity.getId() + "." + ext).normalize();
        if (!file.startsWith(storageDir) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    /** 生成写入正文的根相对 URL。 */
    public String url(WikiImageEntity entity) {
        return URL_PREFIX + entity.getId() + "." + entity.getExt();
    }

    /**
     * 从 URL（{@code /hjjmate/wiki-images/{id}.png} 或 {@code /api/v1/wiki/images/{id}}）
     * 解析出图片 ID；不匹配返回 null。
     */
    public static Long idFromUrl(String url) {
        if (url == null) {
            return null;
        }
        String path = url.split("[?#]", 2)[0];
        String raw = null;
        int wikiIdx = path.indexOf(URL_PREFIX);
        if (wikiIdx >= 0) {
            raw = path.substring(wikiIdx + URL_PREFIX.length()).split("/", 2)[0];
        } else {
            int apiIdx = path.indexOf(API_PATH_PREFIX);
            if (apiIdx >= 0) {
                raw = path.substring(apiIdx + API_PATH_PREFIX.length()).split("/", 2)[0];
            }
        }
        if (raw == null) {
            return null;
        }
        raw = raw.replaceAll("\\.[a-zA-Z0-9]+$", "");
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeExt(String ext, String mimeType) {
        String candidate = ext;
        if ((candidate == null || candidate.isBlank()) && mimeType != null) {
            int slash = mimeType.lastIndexOf('/');
            candidate = slash >= 0 ? mimeType.substring(slash + 1) : mimeType;
        }
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        candidate = candidate.toLowerCase(Locale.ROOT).replace(".", "");
        if ("jpeg".equals(candidate)) {
            candidate = "jpg";
        }
        return ALLOWED_EXT.contains(candidate) ? candidate : null;
    }

    public int deleteByKbId(Long kbId) {
        int delete = imageMapper.delete(new LambdaQueryWrapper<WikiImageEntity>().eq(WikiImageEntity::getKbId, kbId));
        Path resolve = storageDir.resolve(kbId + "");
        try {
            FileUtils.deleteDirectory(resolve.toFile());
        } catch (IOException e) {
            log.warn("[WikiImage] failed to delete image id={}: {}", kbId, e.toString());
        }
        return delete;
    }
}
