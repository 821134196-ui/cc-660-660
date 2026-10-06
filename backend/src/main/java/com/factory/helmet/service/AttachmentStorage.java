package com.factory.helmet.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.factory.helmet.api.BusinessRuleException;
import com.factory.helmet.api.dto.AttachmentInput;

/** 检查附件本地磁盘存储 */
@ApplicationScoped
public class AttachmentStorage {

    @ConfigProperty(name = "safetyhelmet.upload.dir")
    String uploadDir;

    private Path root;

    @PostConstruct
    void init() throws IOException {
        root = Paths.get(uploadDir).toAbsolutePath();
        Files.createDirectories(root);
    }

    public Path getRoot() {
        return root;
    }

    /**
     * 落盘一个附件，返回相对存储路径。
     * 文件名做净化，避免路径穿越；真实文件名另存数据库。
     */
    public StoredFile store(Long inspectionId, AttachmentInput input) {
        if (input == null || input.contentBase64() == null || input.contentBase64().isBlank()) {
            throw BusinessRuleException.badRequest("附件内容为空");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(input.contentBase64());
        } catch (IllegalArgumentException e) {
            throw BusinessRuleException.badRequest("附件不是合法的 Base64 内容");
        }
        if (bytes.length == 0) {
            throw BusinessRuleException.badRequest("附件大小为 0");
        }
        if (bytes.length > 10L * 1024 * 1024) {
            throw BusinessRuleException.badRequest("单个附件不能超过 10MB");
        }
        String safeName = sanitize(input.fileName());
        String contentType = input.contentType() == null || input.contentType().isBlank()
                ? "application/octet-stream" : input.contentType();
        String ext = extension(safeName);
        String stored = "inspection-" + inspectionId + "/" + UUID.randomUUID() + ext;
        Path target = root.resolve(stored);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("附件写入失败: " + e.getMessage(), e);
        }
        return new StoredFile(safeName, stored, contentType, bytes.length);
    }

    /** 读取附件字节；路径必须在存储根目录内 */
    public byte[] read(String storagePath) {
        Path target = root.resolve(storagePath).normalize();
        if (!target.startsWith(root)) {
            throw BusinessRuleException.badRequest("非法附件路径");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw BusinessRuleException.notFound("附件文件不存在");
        }
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "attachment.bin";
        }
        String base = Paths.get(name).getFileName().toString();
        base = base.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
        return base.isBlank() ? "attachment.bin" : base;
    }

    private static String extension(String safeName) {
        int dot = safeName.lastIndexOf('.');
        if (dot < 0 || dot == safeName.length() - 1 || safeName.length() - dot > 10) {
            return "";
        }
        return safeName.substring(dot);
    }

    public record StoredFile(String fileName, String storagePath, String contentType, long sizeBytes) {
    }
}
