package com.suno.mall.controller;

import com.suno.mall.common.ApiResponse;
import com.suno.mall.common.BizException;
import com.suno.mall.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MKT-070 图片上传：白名单扩展名 + 大小限制，落本地目录（mall.upload.dir），
 * 经 /files/** 静态对外。生产可替换为对象存储（实现同一路径契约）。
 */
@Validated
@RestController
@RequestMapping("/api")
public class UploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_BYTES = 5 * 1024 * 1024;

    private final Path uploadDir;

    public UploadController(@Value("${mall.upload.dir:./data/uploads}") String uploadDir) throws IOException {
        this.uploadDir = Path.of(uploadDir);
        Files.createDirectories(this.uploadDir);
    }

    @PostMapping("/uploads")
    public ApiResponse<Map<String, Object>> upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file
    ) {
        if (jwt == null) {
            throw new BizException("请先登录", ErrorCode.AUTH_UNAUTHORIZED);
        }
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空", ErrorCode.PARAM_INVALID);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BizException("图片不能超过 5MB", ErrorCode.PARAM_INVALID);
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String extension = dot >= 0 ? original.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException("仅支持 jpg/png/webp 图片", ErrorCode.PARAM_INVALID);
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            Files.copy(file.getInputStream(), uploadDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new BizException("图片保存失败，请稍后重试", ErrorCode.SYS_INTERNAL_ERROR);
        }
        return ApiResponse.ok(Map.of(
                "url", "/files/" + filename,
                "filename", filename,
                "size", file.getSize()
        ));
    }
}
