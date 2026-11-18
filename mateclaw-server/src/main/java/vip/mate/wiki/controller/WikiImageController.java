package vip.mate.wiki.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.service.AuthService;
import vip.mate.workspace.core.annotation.RequireWorkspaceRole;
import vip.mate.workspace.core.service.WorkspaceService;
import vip.mate.wiki.model.WikiImageEntity;
import vip.mate.wiki.service.WikiImageService;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * 修改人:祝奔；时间：2026-11-18T09:27:23.022Z；修改原因/内容：新增 Wiki 内嵌图片鉴权访问接口，供前端以 JWT + workspace 身份换取 blob 显示；代码来源：AI生成
 */
@Tag(name = "Wiki Images")
@RestController
@RequestMapping("/api/v1/wiki/images")
@RequiredArgsConstructor
public class WikiImageController {

    private final WikiImageService imageService;
    private final AuthService authService;
    private final WorkspaceService workspaceService;

    @Operation(summary = "获取 Wiki 素材内嵌图片（inline，需鉴权）")
    @GetMapping("/{id}")
    @RequireWorkspaceRole("viewer")
    public ResponseEntity<?> serve(@PathVariable Long id,
                                   @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
                                   Authentication authentication) {
        UserEntity user = authentication == null || authentication.getName() == null
                ? null : authService.findByUsername(authentication.getName());
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        Optional<WikiImageEntity> found = imageService.get(id);
        if (found.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Image not found"));
        }
        WikiImageEntity image = found.get();
        if (!canAccess(image, workspaceId, user)) {
            return ResponseEntity.status(403).body(Map.of("error", "Workspace permission denied"));
        }

        Optional<Path> file = imageService.pathOf(image);
        if (file.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Image file missing"));
        }
        String mime = image.getMimeType() != null && !image.getMimeType().isBlank()
                ? image.getMimeType() : "application/octet-stream";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mime))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .body(new FileSystemResource(file.get()));
    }

    private boolean canAccess(WikiImageEntity image, Long currentWorkspaceId, UserEntity user) {
        Long ownerWorkspaceId = image.getWorkspaceId();
        if (ownerWorkspaceId == null) {
            return true;
        }
        boolean isAdmin = "admin".equalsIgnoreCase(user.getRole());
        if (isAdmin) {
            return true;
        }
        if (currentWorkspaceId == null || !ownerWorkspaceId.equals(currentWorkspaceId)) {
            return false;
        }
        return workspaceService.hasPermissionCached(ownerWorkspaceId, user.getId(), "viewer");
    }
}
