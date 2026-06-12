package com.dh.server.avatar;

import com.dh.server.config.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelFileService {

    private final AppConfig appConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从基础模型创建一份新的个性化模型。
     */
    public String createModel(String avatarId, String name, byte[] newTexture,
                               String textureFileName) throws IOException {
        Path generatedDir = getGeneratedDir(avatarId);
        Files.createDirectories(generatedDir);

        Path baseDir = getBaseModelDir();
        copyDirectory(baseDir, generatedDir);

        Path textureDir = findTextureDir(generatedDir);
        if (textureDir != null) {
            Path textureFile = textureDir.resolve(textureFileName);
            Files.write(textureFile, newTexture);
        } else {
            Files.write(generatedDir.resolve(textureFileName), newTexture);
        }

        byte[] thumbnail = generateThumbnail(newTexture);
        Path thumbnailFile = generatedDir.resolve("thumbnail.png");
        Files.write(thumbnailFile, thumbnail);

        MetaInfo meta = new MetaInfo();
        meta.setId(avatarId);
        meta.setName(name);
        meta.setCreatedAt(java.time.LocalDateTime.now().toString());
        String metaJson = objectMapper.writeValueAsString(meta);
        Files.write(generatedDir.resolve("meta.json"),
            metaJson.getBytes(StandardCharsets.UTF_8));

        Path modelJson = findModelJson(generatedDir);
        Path modelsRoot = getModelsRoot();
        return "/models/" + modelsRoot.relativize(modelJson).toString().replace('\\', '/');
    }

    public List<AvatarInfo> listAvatars() throws IOException {
        Path generatedDir = getGeneratedRoot();
        if (!Files.exists(generatedDir)) {
            return Collections.emptyList();
        }

        List<AvatarInfo> avatars = new ArrayList<>();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(generatedDir,
                Files::isDirectory)) {
            for (Path dir : dirs) {
                Path metaFile = dir.resolve("meta.json");
                if (Files.exists(metaFile)) {
                    String json = Files.readString(metaFile);
                    json = stripBom(json);
                    MetaInfo meta = objectMapper.readValue(json, MetaInfo.class);
                    AvatarInfo info = new AvatarInfo();
                    info.setId(meta.getId());
                    info.setName(meta.getName());
                    info.setCreatedAt(meta.getCreatedAt());

                    Path modelJson = findModelJson(dir);
                    Path modelsRoot = getModelsRoot();
                    info.setModelPath("/models/" + modelsRoot.relativize(modelJson)
                        .toString().replace('\\', '/'));
                    info.setThumbnailPath("/models/" + modelsRoot.relativize(
                        dir.resolve("thumbnail.png")).toString().replace('\\', '/'));

                    avatars.add(info);
                }
            }
        }
        avatars.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return avatars;
    }

    public void deleteAvatar(String avatarId) throws IOException {
        Path dir = getGeneratedDir(avatarId);
        if (!Files.exists(dir)) {
            throw new IllegalArgumentException("形象不存在: " + avatarId);
        }
        deleteDirectory(dir);
    }

    public byte[] loadBaseTexture() throws IOException {
        Path baseDir = getBaseModelDir();
        Path textureDir = findTextureDir(baseDir);
        if (textureDir == null) {
            throw new IOException("基础模型纹理目录未找到");
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(textureDir, "*.png")) {
            for (Path file : files) {
                return Files.readAllBytes(file);
            }
        }
        throw new IOException("基础模型纹理文件未找到");
    }

    public AvatarInfo getAvatarInfo(String avatarId) throws IOException {
        Path dir = getGeneratedDir(avatarId);
        Path metaFile = dir.resolve("meta.json");
        if (!Files.exists(metaFile)) {
            throw new IllegalArgumentException("形象不存在: " + avatarId);
        }
        String json = Files.readString(metaFile);
        json = stripBom(json);
        MetaInfo meta = objectMapper.readValue(json, MetaInfo.class);
        AvatarInfo info = new AvatarInfo();
        info.setId(meta.getId());
        info.setName(meta.getName());
        info.setCreatedAt(meta.getCreatedAt());

        Path modelJson = findModelJson(dir);
        Path modelsRoot = getModelsRoot();
        info.setModelPath("/models/" + modelsRoot.relativize(modelJson)
            .toString().replace('\\', '/'));
        info.setThumbnailPath("/models/" + modelsRoot.relativize(
            dir.resolve("thumbnail.png")).toString().replace('\\', '/'));
        return info;
    }

    private String stripBom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '﻿') {
            return s.substring(1);
        }
        return s;
    }

    private Path getModelsRoot() {
        return Path.of(appConfig.getAvatar().getModelsDir());
    }

    private Path getBaseModelDir() {
        return getModelsRoot().resolve("base");
    }

    private Path getGeneratedRoot() {
        return getModelsRoot().resolve("generated");
    }

    private Path getGeneratedDir(String avatarId) {
        return getGeneratedRoot().resolve(avatarId);
    }

    private Path findModelJson(Path dir) throws IOException {
        // 优先查找 Cubism 3+ .model3.json
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.model3.json")) {
            for (Path file : files) {
                return file;
            }
        }
        // 回退到 Cubism 2 .model.json
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.model.json")) {
            for (Path file : files) {
                // 排除 .model3.json（上面已经处理过）
                if (!file.getFileName().toString().endsWith(".model3.json")) {
                    return file;
                }
            }
        }
        throw new IOException("模型 JSON 文件未找到 (.model3.json 或 .model.json): " + dir);
    }

    private Path findTextureDir(Path modelDir) throws IOException {
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(modelDir,
                p -> Files.isDirectory(p) && p.getFileName().toString().endsWith(".2048"))) {
            for (Path dir : dirs) {
                return dir;
            }
        }
        return null;
    }

    private byte[] generateThumbnail(byte[] texture) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(texture));
        if (img == null) return texture;
        int thumbSize = 128;
        BufferedImage thumb = new BufferedImage(thumbSize, thumbSize,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = thumb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, thumbSize, thumbSize, null);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(thumb, "PNG", baos);
        return baos.toByteArray();
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(src -> {
            try {
                Path dest = target.resolve(source.relativize(src));
                if (Files.isDirectory(src)) {
                    if (!Files.exists(dest)) Files.createDirectories(dest);
                } else {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        }
    }

    @Data
    public static class AvatarInfo {
        private String id;
        private String name;
        private String modelPath;
        private String thumbnailPath;
        private String createdAt;
    }

    @Data
    private static class MetaInfo {
        private String id;
        private String name;
        private String createdAt;
    }
}
