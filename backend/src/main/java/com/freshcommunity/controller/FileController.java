package com.freshcommunity.controller;

import com.freshcommunity.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class FileController {

    /** 图片类型白名单：扩展名 + contentType 双重校验（该目录经 /uploads/** 公开可访问） */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    @Value("${upload.path}")
    private String uploadPath;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String ext = safeExtension(file.getOriginalFilename());
        if (ext == null || !IMAGE_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("文件名必须带图片扩展名（jpg/jpeg/png/gif/webp）");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("仅支持上传图片文件");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        Path dir = Paths.get(uploadPath);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        String filename = UUID.randomUUID() + "." + ext;
        Files.copy(file.getInputStream(), dir.resolve(filename));
        return Result.success("/uploads/" + filename);
    }

    /**
     * 安全取小写扩展名：文件名为 null / 无点 / 点在末尾（"photo."）一律返回 null，
     * 替代此前 lastIndexOf 直接 substring 的写法（无扩展名时越界、null 时 NPE）。
     */
    private String safeExtension(String originalFilename) {
        if (originalFilename == null) {
            return null;
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return null;
        }
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
