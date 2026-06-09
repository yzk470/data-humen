# 二次元形象换皮 Live2D 数字人 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 管理员上传二次元图片 → 前端裁剪框选面部 → 服务端等比缩放+纹理合成 → 生成个性化 Live2D 形象，终端用户可在对话页切换选择。

**Architecture:** 新建 `avatar` 包包含 TextureService（图像处理）、ModelFileService（文件管理）、AvatarService（流水线编排）。AdminController 扩展上传/列表/删除 API，新建 AvatarController 提供公开形象列表，SessionController 新增用户切换形象 API。前端新增 ImageCropper 裁剪组件，AdminPanel 集成上传流程，ChatView 增加形象切换入口。

**Tech Stack:** Spring Boot 3.x + Java BufferedImage（图像处理）+ Vue 3 + Canvas API（前端裁剪）+ Live2D Cubism SDK Sample Model

**Spec Reference:** `docs/superpowers/specs/2026-06-09-photo-to-live2d-design.md`

---

## 文件结构映射

```
digital-human-server/
├── src/main/java/com/dh/server/
│   ├── config/
│   │   └── AppConfig.java                        # 修改: 新增 AvatarConfig
│   ├── avatar/
│   │   ├── TextureService.java                   # 新建: 裁剪+缩放+合成+羽化
│   │   ├── ModelFileService.java                 # 新建: 模型文件 CRUD
│   │   └── AvatarService.java                    # 新建: 流水线编排
│   └── controller/
│       ├── AdminController.java                  # 修改: 新增上传/列表/删除
│       ├── SessionController.java                # 修改: 新增切换形象 API
│       └── AvatarController.java                 # 新建: 公开形象列表 API
├── src/main/resources/
│   └── application.yml                           # 修改: 新增 app.avatar 配置
└── src/test/java/com/dh/server/avatar/
    ├── TextureServiceTest.java                   # 新建
    ├── ModelFileServiceTest.java                 # 新建
    └── AvatarServiceTest.java                    # 新建

digital-human-web/
├── src/
│   ├── components/
│   │   ├── ImageCropper.vue                      # 新建: 可视化裁剪组件
│   │   ├── AdminPanel.vue                        # 修改: 集成裁剪+上传+形象库
│   │   ├── ChatView.vue                          # 修改: 形象切换菜单
│   │   └── Live2DCanvas.vue                      # 修改: 动态切换 modelPath
│   ├── services/
│   │   └── api.js                                # 修改: 新增 avatar API
│   └── stores/
│       └── avatar.js                             # 修改: 新增 actions
└── public/
    └── models/                                   # 模型文件目录（手动部署基础模型）
```

---

## 里程碑 1：后端核心服务

### Task 1: AppConfig 配置扩展 + application.yml

**Files:**
- Modify: `digital-human-server/src/main/java/com/dh/server/config/AppConfig.java`
- Modify: `digital-human-server/src/main/resources/application.yml`

- [ ] **Step 1: 在 AppConfig 中新增 AvatarConfig 内部类**

```java
package com.dh.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {
    private DeepSeek deepseek = new DeepSeek();
    private Avatar avatar = new Avatar();

    @Data
    public static class DeepSeek {
        private String apiKey;
        private String apiUrl = "https://api.deepseek.com/v1/chat/completions";
        private String model = "deepseek-chat";
    }

    @Data
    public static class Avatar {
        /** 基础 Live2D 模型 JSON 路径，相对于 modelsRoot */
        private String baseModel = "models/base/haru.model3.json";
        /** 面部在纹理图集中的矩形区域 */
        private FaceRegion faceRegion = new FaceRegion();
        /** 模型文件根目录（相对于 static 资源目录） */
        private String modelsDir = "digital-human-web/public/models";

        @Data
        public static class FaceRegion {
            private int x = 512;
            private int y = 128;
            private int width = 256;
            private int height = 256;
        }
    }
}
```

- [ ] **Step 2: 在 application.yml 中新增 avatar 配置**

在 `application.yml` 末尾追加：

```yaml
app:
  avatar:
    base-model: models/base/haru.model3.json
    models-dir: digital-human-web/public/models
    face-region:
      x: 512
      y: 128
      width: 256
      height: 256
```

- [ ] **Step 3: 验证编译**

```bash
cd digital-human-server
mvn compile
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/config/AppConfig.java
git add digital-human-server/src/main/resources/application.yml
git commit -m "feat: add avatar config properties (base model, face region)"
```

---

### Task 2: TextureService — 图像处理服务

**Files:**
- Create: `digital-human-server/src/main/java/com/dh/server/avatar/TextureService.java`

- [ ] **Step 1: 创建 TextureService 类**

```java
package com.dh.server.avatar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class TextureService {

    /**
     * 将管理员框选的面部区域替换到基础纹理中。
     *
     * @param sourceImage 上传的原图字节
     * @param cropX       裁剪起点 X（相对于原图）
     * @param cropY       裁剪起点 Y（相对于原图）
     * @param cropW       裁剪宽度
     * @param cropH       裁剪高度
     * @param baseTexture 基础纹理图集字节
     * @param faceRegion  面部在图集中的矩形区域
     * @return 合成后的纹理 PNG 字节数组
     */
    public byte[] replaceFaceRegion(byte[] sourceImage,
                                     int cropX, int cropY, int cropW, int cropH,
                                     byte[] baseTexture, Rectangle faceRegion) {
        try {
            // Step 1: 按坐标裁剪
            BufferedImage source = readImage(sourceImage);
            validateCropBounds(source, cropX, cropY, cropW, cropH);
            BufferedImage cropped = source.getSubimage(cropX, cropY, cropW, cropH);

            // Step 2: 等比缩放到面部区域尺寸
            BufferedImage scaled = resizeToFit(cropped,
                (int) faceRegion.getWidth(), (int) faceRegion.getHeight());

            // Step 3: 纹理合成
            BufferedImage base = readImage(baseTexture);
            Graphics2D g = base.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(scaled, (int) faceRegion.getX(), (int) faceRegion.getY(), null);
            g.dispose();

            // Step 4: 边缘羽化
            featherEdges(base, faceRegion, 10);

            return toPngBytes(base);
        } catch (Exception e) {
            log.error("纹理合成失败", e);
            throw new RuntimeException("纹理合成失败: " + e.getMessage());
        }
    }

    private BufferedImage readImage(byte[] bytes) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) {
            throw new IOException("无法解析图片，请确认文件格式为 PNG/JPG");
        }
        return img;
    }

    private void validateCropBounds(BufferedImage img, int x, int y, int w, int h) {
        if (x < 0 || y < 0 || x + w > img.getWidth() || y + h > img.getHeight()) {
            throw new IllegalArgumentException(
                String.format("裁剪区域超出图片范围: 图片=%dx%d, 裁剪=[%d,%d,%d,%d]",
                    img.getWidth(), img.getHeight(), x, y, w, h));
        }
        if (w < 64 || h < 64) {
            throw new IllegalArgumentException(
                String.format("裁剪区域过小: %dx%d, 最小 64x64", w, h));
        }
    }

    /**
     * 等比缩放：保持宽高比，居中适配到目标尺寸。
     * 缩放后若与目标尺寸不完全一致，居中绘制。
     */
    private BufferedImage resizeToFit(BufferedImage source, int targetW, int targetH) {
        // 计算等比缩放后的尺寸（取较大的缩放比例以保证填满）
        double scale = Math.max(
            (double) targetW / source.getWidth(),
            (double) targetH / source.getHeight()
        );
        int scaledW = (int) (source.getWidth() * scale);
        int scaledH = (int) (source.getHeight() * scale);

        // 创建缩放后的图片
        BufferedImage scaled = new BufferedImage(scaledW, scaledH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source, 0, 0, scaledW, scaledH, null);
        g.dispose();

        // 创建目标尺寸的画布，居中绘制
        BufferedImage result = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        int offsetX = (targetW - scaledW) / 2;
        int offsetY = (targetH - scaledH) / 2;
        g2.drawImage(scaled, offsetX, offsetY, null);
        g2.dispose();

        return result;
    }

    /**
     * 边缘羽化：对替换区域边界做 alpha 渐变，使过渡自然。
     *
     * @param image      已合成面部的纹理图
     * @param faceRegion 面部区域
     * @param featherPx  羽化像素宽度
     */
    private void featherEdges(BufferedImage image, Rectangle faceRegion, int featherPx) {
        int x1 = (int) faceRegion.getX();
        int y1 = (int) faceRegion.getY();
        int x2 = x1 + (int) faceRegion.getWidth();
        int y2 = y1 + (int) faceRegion.getHeight();

        for (int y = y1; y < y2; y++) {
            for (int x = x1; x < x2; x++) {
                int distToEdge = Math.min(
                    Math.min(x - x1, x2 - x - 1),
                    Math.min(y - y1, y2 - y - 1)
                );
                if (distToEdge < featherPx) {
                    float alpha = (float) distToEdge / featherPx;
                    int rgb = image.getRGB(x, y);
                    int a = (rgb >> 24) & 0xff;
                    int r = (rgb >> 16) & 0xff;
                    int g = (rgb >> 8) & 0xff;
                    int b = rgb & 0xff;
                    int newA = (int) (a * alpha);
                    image.setRGB(x, y, (newA << 24) | (r << 16) | (g << 8) | b);
                }
            }
        }
    }

    private byte[] toPngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd digital-human-server
mvn compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/avatar/TextureService.java
git commit -m "feat: add TextureService - crop, proportional resize, composite, feather edges"
```

---

### Task 3: TextureService 单元测试

**Files:**
- Create: `digital-human-server/src/test/java/com/dh/server/avatar/TextureServiceTest.java`

- [ ] **Step 1: 创建测试资源目录和测试图片**

```bash
mkdir -p digital-human-server/src/test/resources/test-images
```

需要准备两张 1x1 像素的 PNG 测试图片。测试代码中通过程序生成测试图片。

- [ ] **Step 2: 编写 TextureServiceTest**

```java
package com.dh.server.avatar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class TextureServiceTest {

    private TextureService textureService;
    private byte[] baseTexture;
    private Rectangle faceRegion;

    @BeforeEach
    void setUp() throws IOException {
        textureService = new TextureService();
        // 创建 512x512 的测试基础纹理（红色背景）
        BufferedImage base = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = base.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 512, 512);
        g.dispose();
        baseTexture = toPngBytes(base);
        // 面部区域：中心 256x256
        faceRegion = new Rectangle(128, 128, 256, 256);
    }

    @Test
    void shouldReplaceFaceRegionSuccessfully() throws IOException {
        // 创建 512x512 的蓝色测试源图
        BufferedImage source = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 512, 512);
        g.dispose();

        byte[] result = textureService.replaceFaceRegion(
            toPngBytes(source),
            64, 64, 256, 256,  // crop: 图片中心 256x256
            baseTexture,
            faceRegion
        );

        assertNotNull(result);
        assertTrue(result.length > 0);

        // 验证输出图片：面部区域应为蓝色，其余为红色
        BufferedImage resultImg = ImageIO.read(new java.io.ByteArrayInputStream(result));
        // 中心点应该在面部区域内
        int centerPixel = resultImg.getRGB(256, 256);
        int blue = centerPixel & 0xFF;
        // 蓝色通道应该较高（蓝色区域）
        assertTrue(blue > 100, "面部区域中心应该偏蓝，实际 blue=" + blue);

        // 角落点应该在红色背景区域
        int cornerPixel = resultImg.getRGB(10, 10);
        int red = (cornerPixel >> 16) & 0xFF;
        assertTrue(red > 100, "背景区域应该偏红，实际 red=" + red);
    }

    @Test
    void shouldThrowExceptionWhenCropOutOfBounds() {
        BufferedImage source = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        byte[] sourceBytes = toPngBytes(source);

        assertThrows(RuntimeException.class, () -> {
            textureService.replaceFaceRegion(
                sourceBytes,
                100, 100, 256, 256,  // crop 超出 200x200 范围
                baseTexture,
                faceRegion
            );
        });
    }

    @Test
    void shouldThrowExceptionWhenCropTooSmall() {
        BufferedImage source = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        byte[] sourceBytes = toPngBytes(source);

        assertThrows(RuntimeException.class, () -> {
            textureService.replaceFaceRegion(
                sourceBytes,
                10, 10, 32, 32,  // 小于 64x64 最小值
                baseTexture,
                faceRegion
            );
        });
    }

    @Test
    void shouldThrowExceptionWhenImageInvalid() {
        byte[] invalidBytes = new byte[]{0x00, 0x01, 0x02};

        assertThrows(RuntimeException.class, () -> {
            textureService.replaceFaceRegion(
                invalidBytes,
                0, 0, 128, 128,
                baseTexture,
                faceRegion
            );
        });
    }

    private byte[] toPngBytes(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
cd digital-human-server
mvn test -Dtest=TextureServiceTest
```
Expected: 4 tests PASS

- [ ] **Step 4: Commit**

```bash
git add digital-human-server/src/test/java/com/dh/server/avatar/TextureServiceTest.java
git commit -m "test: add TextureService unit tests"
```

---

### Task 4: ModelFileService — 模型文件管理

**Files:**
- Create: `digital-human-server/src/main/java/com/dh/server/avatar/ModelFileService.java`

- [ ] **Step 1: 创建 ModelFileService 类**

```java
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
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelFileService {

    private final AppConfig appConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从基础模型创建一份新的个性化模型。
     *
     * @param avatarId        形象 ID
     * @param name            形象名称
     * @param newTexture      替换后的纹理图字节
     * @param textureFileName 纹理文件名 (e.g. "texture_00.png")
     * @return 模型 JSON 文件的静态路径
     */
    public String createModel(String avatarId, String name, byte[] newTexture,
                               String textureFileName) throws IOException {
        Path generatedDir = getGeneratedDir(avatarId);
        Files.createDirectories(generatedDir);

        // 1. 复制基础模型的所有文件（.model3.json, .physics3.json 等）
        Path baseDir = getBaseModelDir();
        copyDirectory(baseDir, generatedDir);

        // 2. 替换纹理文件
        // Haru 模型的纹理在 haru.2048/texture_00.png
        Path textureDir = findTextureDir(generatedDir);
        if (textureDir != null) {
            Path textureFile = textureDir.resolve(textureFileName);
            Files.write(textureFile, newTexture);
        } else {
            // 如果找不到纹理目录，直接写入模型根目录
            Files.write(generatedDir.resolve(textureFileName), newTexture);
        }

        // 3. 生成缩略图 (从新纹理中缩略)
        byte[] thumbnail = generateThumbnail(newTexture);
        Path thumbnailFile = generatedDir.resolve("thumbnail.png");
        Files.write(thumbnailFile, thumbnail);

        // 4. 写入 meta.json
        MetaInfo meta = new MetaInfo();
        meta.setId(avatarId);
        meta.setName(name);
        meta.setCreatedAt(LocalDateTime.now().toString());
        String metaJson = objectMapper.writeValueAsString(meta);
        Files.write(generatedDir.resolve("meta.json"), metaJson.getBytes());

        // 5. 返回模型 JSON 的静态访问路径
        Path modelJson = findModelJson(generatedDir);
        Path modelsRoot = getModelsRoot();
        return "/models/" + modelsRoot.relativize(modelJson).toString().replace('\\', '/');
    }

    /** 列出所有已生成的形象 */
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
                    MetaInfo meta = objectMapper.readValue(json, MetaInfo.class);
                    AvatarInfo info = new AvatarInfo();
                    info.setId(meta.getId());
                    info.setName(meta.getName());
                    info.setCreatedAt(meta.getCreatedAt());

                    // 构建 modelPath 和 thumbnailPath
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
        // 按创建时间倒序
        avatars.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return avatars;
    }

    /** 删除指定形象 */
    public void deleteAvatar(String avatarId) throws IOException {
        Path dir = getGeneratedDir(avatarId);
        if (!Files.exists(dir)) {
            throw new IllegalArgumentException("形象不存在: " + avatarId);
        }
        deleteDirectory(dir);
    }

    /** 读取基础模型的纹理图 */
    public byte[] loadBaseTexture() throws IOException {
        Path baseDir = getBaseModelDir();
        Path textureDir = findTextureDir(baseDir);
        if (textureDir == null) {
            throw new IOException("基础模型纹理目录未找到");
        }
        // 找到第一个 PNG 文件
        try (DirectoryStream<Path> files = Files.newDirectoryStream(textureDir, "*.png")) {
            for (Path file : files) {
                return Files.readAllBytes(file);
            }
        }
        throw new IOException("基础模型纹理文件未找到");
    }

    /** 获取指定形象的信息 */
    public AvatarInfo getAvatarInfo(String avatarId) throws IOException {
        Path dir = getGeneratedDir(avatarId);
        Path metaFile = dir.resolve("meta.json");
        if (!Files.exists(metaFile)) {
            throw new IllegalArgumentException("形象不存在: " + avatarId);
        }
        String json = Files.readString(metaFile);
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

    // ---- 私有辅助方法 ----

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

    /** 在模型目录中查找 .model3.json 文件 */
    private Path findModelJson(Path dir) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.model3.json")) {
            for (Path file : files) {
                return file;
            }
        }
        throw new IOException("模型 JSON 文件未找到 (.model3.json): " + dir);
    }

    /** 查找纹理目录（通常是 xxx.2048/ 格式） */
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
```

- [ ] **Step 2: 验证编译**

```bash
cd digital-human-server
mvn compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/avatar/ModelFileService.java
git commit -m "feat: add ModelFileService - model directory management, meta.json, thumbnails"
```

---

### Task 5: ModelFileService 单元测试

**Files:**
- Create: `digital-human-server/src/test/java/com/dh/server/avatar/ModelFileServiceTest.java`

- [ ] **Step 1: 编写 ModelFileServiceTest**

```java
package com.dh.server.avatar;

import com.dh.server.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelFileServiceTest {

    @TempDir
    Path tempDir;

    private ModelFileService modelFileService;
    private AppConfig appConfig;

    @BeforeEach
    void setUp() throws IOException {
        appConfig = new AppConfig();
        appConfig.getAvatar().setModelsDir(tempDir.toString());
        appConfig.getAvatar().setBaseModel("models/base/haru.model3.json");
        appConfig.getAvatar().getFaceRegion().setX(512);
        appConfig.getAvatar().getFaceRegion().setY(128);
        appConfig.getAvatar().getFaceRegion().setWidth(256);
        appConfig.getAvatar().getFaceRegion().setHeight(256);
        modelFileService = new ModelFileService(appConfig);

        // 创建测试基础模型目录结构
        Path baseModelDir = tempDir.resolve("models/base");
        Files.createDirectories(baseModelDir);
        Files.writeString(baseModelDir.resolve("haru.model3.json"),
            "{\"version\":\"3.0\"}");

        Path textureDir = baseModelDir.resolve("haru.2048");
        Files.createDirectories(textureDir);
        BufferedImage tex = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tex.createGraphics();
        g.setColor(Color.PINK);
        g.fillRect(0, 0, 1024, 1024);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(tex, "PNG", baos);
        Files.write(textureDir.resolve("texture_00.png"), baos.toByteArray());
    }

    @Test
    void shouldCreateModelAndListAvatars() throws IOException {
        // 创建新纹理
        BufferedImage newTex = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = newTex.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 256, 256);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(newTex, "PNG", baos);

        // 创建模型
        String modelPath = modelFileService.createModel(
            "avatar_test_001", "测试形象", baos.toByteArray(), "texture_00.png");

        assertNotNull(modelPath);
        assertTrue(modelPath.contains("avatar_test_001"));
        assertTrue(modelPath.endsWith(".model3.json"));

        // 验证文件存在
        Path genDir = tempDir.resolve("models/generated/avatar_test_001");
        assertTrue(Files.exists(genDir.resolve("haru.model3.json")));
        assertTrue(Files.exists(genDir.resolve("meta.json")));
        assertTrue(Files.exists(genDir.resolve("thumbnail.png")));

        // 验证 meta.json 内容
        String meta = Files.readString(genDir.resolve("meta.json"));
        assertTrue(meta.contains("测试形象"));
        assertTrue(meta.contains("avatar_test_001"));

        // 验证列表
        List<ModelFileService.AvatarInfo> avatars = modelFileService.listAvatars();
        assertEquals(1, avatars.size());
        assertEquals("测试形象", avatars.get(0).getName());
    }

    @Test
    void shouldDeleteAvatar() throws IOException {
        // 先创建一个
        byte[] texBytes = createTestTexture();
        modelFileService.createModel("avatar_del", "待删除", texBytes, "texture_00.png");

        // 验证存在
        assertEquals(1, modelFileService.listAvatars().size());

        // 删除
        modelFileService.deleteAvatar("avatar_del");

        // 验证已删除
        assertEquals(0, modelFileService.listAvatars().size());
    }

    @Test
    void shouldThrowExceptionWhenAvatarNotFound() {
        assertThrows(IllegalArgumentException.class, () -> {
            modelFileService.deleteAvatar("nonexistent");
        });
    }

    @Test
    void shouldLoadBaseTexture() throws IOException {
        byte[] baseTex = modelFileService.loadBaseTexture();
        assertNotNull(baseTex);
        assertTrue(baseTex.length > 0);
    }

    @Test
    void shouldReturnEmptyListWhenNoAvatars() throws IOException {
        List<ModelFileService.AvatarInfo> avatars = modelFileService.listAvatars();
        assertNotNull(avatars);
        assertTrue(avatars.isEmpty());
    }

    private byte[] createTestTexture() throws IOException {
        BufferedImage tex = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tex.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, 256, 256);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(tex, "PNG", baos);
        return baos.toByteArray();
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
cd digital-human-server
mvn test -Dtest=ModelFileServiceTest
```
Expected: 5 tests PASS

- [ ] **Step 3: Commit**

```bash
git add digital-human-server/src/test/java/com/dh/server/avatar/ModelFileServiceTest.java
git commit -m "test: add ModelFileService unit tests"
```

---

### Task 6: AvatarService — 流水线编排

**Files:**
- Create: `digital-human-server/src/main/java/com/dh/server/avatar/AvatarService.java`

- [ ] **Step 1: 创建 AvatarService 类**

```java
package com.dh.server.avatar;

import com.dh.server.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarService {

    private final TextureService textureService;
    private final ModelFileService modelFileService;
    private final AppConfig appConfig;

    /**
     * 换皮流水线：
     *   二次元图片 → 裁剪 → 等比缩放 → 纹理合成 → 保存模型
     *
     * @param imageBytes 上传的原始图片
     * @param name       形象名称
     * @param cropX, cropY, cropW, cropH  前端传来的裁剪坐标
     * @return 生成的形象信息
     */
    public ModelFileService.AvatarInfo generateAvatar(byte[] imageBytes, String name,
                                                       int cropX, int cropY,
                                                       int cropW, int cropH)
            throws IOException {
        // 1. 读取基础纹理
        byte[] baseTexture = modelFileService.loadBaseTexture();

        // 2. 面部替换
        AppConfig.Avatar.FaceRegion fr = appConfig.getAvatar().getFaceRegion();
        Rectangle faceRegion = new Rectangle(fr.getX(), fr.getY(),
            fr.getWidth(), fr.getHeight());
        byte[] newTexture = textureService.replaceFaceRegion(
            imageBytes, cropX, cropY, cropW, cropH,
            baseTexture, faceRegion
        );

        // 3. 创建模型文件
        String avatarId = "avatar_" + System.currentTimeMillis();
        modelFileService.createModel(avatarId, name, newTexture, "texture_00.png");

        // 4. 返回形象信息
        return modelFileService.getAvatarInfo(avatarId);
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd digital-human-server
mvn compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/avatar/AvatarService.java
git commit -m "feat: add AvatarService - texture swap pipeline orchestration"
```

---

### Task 7: AvatarService 单元测试

**Files:**
- Create: `digital-human-server/src/test/java/com/dh/server/avatar/AvatarServiceTest.java`

- [ ] **Step 1: 编写 AvatarServiceTest**

```java
package com.dh.server.avatar;

import com.dh.server.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AvatarServiceTest {

    private TextureService textureService;
    private ModelFileService modelFileService;
    private AppConfig appConfig;
    private AvatarService avatarService;

    @BeforeEach
    void setUp() {
        textureService = mock(TextureService.class);
        modelFileService = mock(ModelFileService.class);
        appConfig = new AppConfig();
        appConfig.getAvatar().getFaceRegion().setX(512);
        appConfig.getAvatar().getFaceRegion().setY(128);
        appConfig.getAvatar().getFaceRegion().setWidth(256);
        appConfig.getAvatar().getFaceRegion().setHeight(256);
        avatarService = new AvatarService(textureService, modelFileService, appConfig);
    }

    @Test
    void shouldCompleteFullPipeline() throws IOException {
        byte[] image = new byte[]{0x01, 0x02};
        byte[] baseTex = new byte[]{0x03, 0x04};
        byte[] newTex = new byte[]{0x05, 0x06};

        when(modelFileService.loadBaseTexture()).thenReturn(baseTex);
        when(textureService.replaceFaceRegion(eq(image),
            eq(100), eq(50), eq(300), eq(300),
            eq(baseTex), any())).thenReturn(newTex);

        ModelFileService.AvatarInfo expectedInfo = new ModelFileService.AvatarInfo();
        expectedInfo.setId("avatar_123");
        expectedInfo.setName("测试");
        expectedInfo.setModelPath("/models/generated/avatar_123/haru.model3.json");
        expectedInfo.setThumbnailPath("/models/generated/avatar_123/thumbnail.png");
        expectedInfo.setCreatedAt("2026-06-09T12:00:00");

        when(modelFileService.getAvatarInfo(anyString())).thenReturn(expectedInfo);

        ModelFileService.AvatarInfo result = avatarService.generateAvatar(
            image, "测试", 100, 50, 300, 300);

        assertNotNull(result);
        assertEquals("测试", result.getName());
        assertEquals("/models/generated/avatar_123/haru.model3.json", result.getModelPath());

        verify(modelFileService).loadBaseTexture();
        verify(textureService).replaceFaceRegion(eq(image),
            eq(100), eq(50), eq(300), eq(300), eq(baseTex), any());
        verify(modelFileService).createModel(anyString(), eq("测试"),
            eq(newTex), eq("texture_00.png"));
    }

    @Test
    void shouldPropagateTextureServiceException() throws IOException {
        when(modelFileService.loadBaseTexture()).thenReturn(new byte[]{0x01});
        when(textureService.replaceFaceRegion(any(), anyInt(), anyInt(),
            anyInt(), anyInt(), any(), any()))
            .thenThrow(new RuntimeException("纹理合成失败"));

        assertThrows(RuntimeException.class, () -> {
            avatarService.generateAvatar(new byte[]{0x01}, "bad",
                0, 0, 256, 256);
        });

        // 不应该调用 createModel
        verify(modelFileService, never()).createModel(anyString(), anyString(),
            any(), anyString());
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
cd digital-human-server
mvn test -Dtest=AvatarServiceTest
```
Expected: 2 tests PASS

- [ ] **Step 3: Commit**

```bash
git add digital-human-server/src/test/java/com/dh/server/avatar/AvatarServiceTest.java
git commit -m "test: add AvatarService unit tests"
```

---

## 里程碑 2：后端 API

### Task 8: AdminController 扩展 — 形象管理 API

**Files:**
- Modify: `digital-human-server/src/main/java/com/dh/server/controller/AdminController.java`

- [ ] **Step 1: 在 AdminController 中新增三个端点**

在现有 AdminController 类末尾（`}` 之前）添加以下代码：

```java
    private final AvatarService avatarService;
    private final ModelFileService modelFileService;

    // 构造函数需要更新为包含新依赖
    // 修改原有的 @RequiredArgsConstructor 生成的构造函数
    // 由于新加了 final 字段，Lombok 会自动生成包含所有字段的构造函数

    @PostMapping("/avatar/upload")
    public Result<ModelFileService.AvatarInfo> uploadAvatar(
            @RequestParam("image") MultipartFile image,
            @RequestParam("name") String name,
            @RequestParam("cropX") int cropX,
            @RequestParam("cropY") int cropY,
            @RequestParam("cropW") int cropW,
            @RequestParam("cropH") int cropH) {

        if (image.isEmpty()) {
            throw new BusinessException(400, "请选择图片文件");
        }

        String contentType = image.getContentType();
        if (contentType == null ||
            (!contentType.equals("image/png") && !contentType.equals("image/jpeg"))) {
            throw new BusinessException(400, "仅支持 PNG/JPG 格式");
        }

        if (image.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException(400, "图片大小不能超过 10MB");
        }

        try {
            byte[] imageBytes = image.getBytes();
            return Result.ok(avatarService.generateAvatar(
                imageBytes, name, cropX, cropY, cropW, cropH));
        } catch (IOException e) {
            log.error("形象生成失败", e);
            throw new BusinessException(500, "形象生成失败: " + e.getMessage());
        }
    }

    @GetMapping("/avatar/list")
    public Result<Map<String, Object>> listAvatars() {
        try {
            List<ModelFileService.AvatarInfo> avatars = modelFileService.listAvatars();
            String defaultId = avatars.isEmpty() ? "" : avatars.get(0).getId();
            return Result.ok(Map.of(
                "avatars", avatars,
                "defaultId", defaultId
            ));
        } catch (IOException e) {
            log.error("读取形象列表失败", e);
            throw new BusinessException(500, "读取形象列表失败");
        }
    }

    @DeleteMapping("/avatar/{id}")
    public Result<Map<String, Boolean>> deleteAvatar(@PathVariable String id) {
        try {
            // 检查是否是默认形象（第一个）
            List<ModelFileService.AvatarInfo> avatars = modelFileService.listAvatars();
            if (!avatars.isEmpty() && avatars.get(0).getId().equals(id)) {
                throw new BusinessException(400, "不能删除默认形象");
            }
            modelFileService.deleteAvatar(id);
            return Result.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(404, e.getMessage());
        } catch (IOException e) {
            log.error("删除形象失败", e);
            throw new BusinessException(500, "删除形象失败");
        }
    }
```

- [ ] **Step 2: 更新 AdminController 的 import 和字段声明**

在 AdminController 文件顶部，新增 import：

```java
import com.dh.server.avatar.AvatarService;
import com.dh.server.avatar.ModelFileService;
import com.dh.server.common.BusinessException;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
```

在字段声明区新增（`private final ConfigStorageService configStorageService;` 之后）：

```java
    private final AvatarService avatarService;
    private final ModelFileService modelFileService;
```

- [ ] **Step 3: 验证编译**

```bash
cd digital-human-server
mvn compile
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/controller/AdminController.java
git commit -m "feat: add avatar upload/list/delete admin APIs"
```

---

### Task 9: 公开形象 API

**Files:**
- Create: `digital-human-server/src/main/java/com/dh/server/controller/AvatarController.java`
- Modify: `digital-human-server/src/main/java/com/dh/server/controller/SessionController.java`

- [ ] **Step 1: 创建 AvatarController（公开形象列表）**

```java
package com.dh.server.controller;

import com.dh.server.avatar.ModelFileService;
import com.dh.server.common.BusinessException;
import com.dh.server.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/avatar")
@RequiredArgsConstructor
public class AvatarController {

    private final ModelFileService modelFileService;

    @GetMapping("/list")
    public Result<Map<String, Object>> listAvatars() {
        try {
            List<ModelFileService.AvatarInfo> avatars = modelFileService.listAvatars();
            String defaultId = avatars.isEmpty() ? "" : avatars.get(0).getId();
            return Result.ok(Map.of(
                "avatars", avatars,
                "defaultId", defaultId
            ));
        } catch (IOException e) {
            log.error("读取形象列表失败", e);
            throw new BusinessException(500, "读取形象列表失败");
        }
    }
}
```

- [ ] **Step 2: 在 SessionController 中新增切换形象 API**

在 SessionController 类末尾（`}` 之前）添加：

```java
    private final ModelFileService modelFileService;

    @PutMapping("/{sessionId}/avatar")
    public Result<Map<String, String>> switchAvatar(@PathVariable String sessionId,
                                                     @RequestBody Map<String, String> body) {
        Session session = sessionManager.getSession(sessionId);
        if (session == null) {
            return Result.fail(404, "会话不存在");
        }

        String avatarId = body.get("avatarId");
        if (avatarId == null || avatarId.isEmpty()) {
            return Result.fail(400, "avatarId 不能为空");
        }

        try {
            ModelFileService.AvatarInfo info = modelFileService.getAvatarInfo(avatarId);
            return Result.ok(Map.of("modelPath", info.getModelPath()));
        } catch (IllegalArgumentException e) {
            return Result.fail(404, "形象不存在: " + avatarId);
        } catch (IOException e) {
            log.error("切换形象失败", e);
            return Result.fail(500, "切换形象失败");
        }
    }
```

并在 SessionController 文件头部新增 import：

```java
import com.dh.server.avatar.ModelFileService;
import java.io.IOException;
```

- [ ] **Step 3: 验证编译**

```bash
cd digital-human-server
mvn compile
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/controller/AvatarController.java
git add digital-human-server/src/main/java/com/dh/server/controller/SessionController.java
git commit -m "feat: add public avatar list API and session avatar switch API"
```

---

## 里程碑 3：前端实现

### Task 10: ImageCropper.vue — 可视化裁剪组件

**Files:**
- Create: `digital-human-web/src/components/ImageCropper.vue`

- [ ] **Step 1: 创建 ImageCropper 组件**

```vue
<template>
  <div class="image-cropper">
    <div class="cropper-canvas-wrapper" ref="wrapperRef">
      <canvas
        ref="canvasRef"
        :width="canvasSize"
        :height="canvasSize"
        @mousedown="onMouseDown"
        @mousemove="onMouseMove"
        @mouseup="onMouseUp"
        @mouseleave="onMouseUp"
      ></canvas>
      <div v-if="!imageLoaded" class="upload-hint">
        请先选择一张二次元图片
      </div>
    </div>
    <div class="crop-info" v-if="imageLoaded">
      裁剪区域: {{ cropX }}, {{ cropY }} — {{ cropW }} × {{ cropH }}
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'

const props = defineProps({
  imageFile: {
    type: File,
    default: null
  }
})

const emit = defineEmits(['crop-change'])

const canvasSize = 400
const canvasRef = ref(null)
const wrapperRef = ref(null)
const imageLoaded = ref(false)

const cropX = ref(50)
const cropY = ref(50)
const cropW = ref(200)
const cropH = ref(200)

let img = null
let dragging = false
let resizing = false
let dragStartX = 0
let dragStartY = 0
let dragStartCropX = 0
let dragStartCropY = 0
let dragStartCropW = 0
let dragStartCropH = 0

const MIN_CROP = 64

watch(() => props.imageFile, async (file) => {
  if (!file) return
  const reader = new FileReader()
  reader.onload = (e) => {
    img = new Image()
    img.onload = () => {
      imageLoaded.value = true
      // 初始化裁剪框为图片中心的正方形
      initCropBox()
      nextTick(() => draw())
    }
    img.src = e.target.result
  }
  reader.readAsDataURL(file)
})

function initCropBox() {
  const imgW = img.naturalWidth
  const imgH = img.naturalHeight
  const size = Math.min(imgW, imgH, 300)
  cropX.value = Math.round((imgW - size) / 2)
  cropY.value = Math.round((imgH - size) / 2)
  cropW.value = size
  cropH.value = size
  emitCrop()
}

function draw() {
  const canvas = canvasRef.value
  if (!canvas || !img) return
  const ctx = canvas.getContext('2d')
  const scale = canvasSize / Math.max(img.naturalWidth, img.naturalHeight)

  // 绘制图片
  const drawW = img.naturalWidth * scale
  const drawH = img.naturalHeight * scale
  const offsetX = (canvasSize - drawW) / 2
  const offsetY = (canvasSize - drawH) / 2

  ctx.clearRect(0, 0, canvasSize, canvasSize)
  ctx.drawImage(img, offsetX, offsetY, drawW, drawH)

  // 绘制裁剪框
  const cx = offsetX + cropX.value * scale
  const cy = offsetY + cropY.value * scale
  const cw = cropW.value * scale
  const ch = cropH.value * scale

  // 半透明遮罩
  ctx.fillStyle = 'rgba(0, 0, 0, 0.5)'
  ctx.fillRect(0, 0, canvasSize, canvasSize)
  ctx.clearRect(cx, cy, cw, ch)
  ctx.drawImage(img,
    cropX.value, cropY.value, cropW.value, cropH.value,
    cx, cy, cw, ch
  )

  // 裁剪框边框
  ctx.strokeStyle = '#409EFF'
  ctx.lineWidth = 2
  ctx.strokeRect(cx, cy, cw, ch)

  // 四角抓手
  const handleSize = 8
  ctx.fillStyle = '#409EFF'
  ctx.fillRect(cx - handleSize / 2, cy - handleSize / 2, handleSize, handleSize)
  ctx.fillRect(cx + cw - handleSize / 2, cy - handleSize / 2, handleSize, handleSize)
  ctx.fillRect(cx - handleSize / 2, cy + ch - handleSize / 2, handleSize, handleSize)
  ctx.fillRect(cx + cw - handleSize / 2, cy + ch - handleSize / 2, handleSize, handleSize)
}

function getScale() {
  return canvasSize / Math.max(img.naturalWidth, img.naturalHeight)
}

function canvasToImage(canvasX, canvasY) {
  const scale = getScale()
  const drawW = img.naturalWidth * scale
  const drawH = img.naturalHeight * scale
  const offsetX = (canvasSize - drawW) / 2
  const offsetY = (canvasSize - drawH) / 2
  return {
    x: Math.round((canvasX - offsetX) / scale),
    y: Math.round((canvasY - offsetY) / scale)
  }
}

function isNearCorner(canvasX, canvasY, cornerX, cornerY) {
  return Math.abs(canvasX - cornerX) < 10 && Math.abs(canvasY - cornerY) < 10
}

function onMouseDown(e) {
  if (!img) return
  const rect = canvasRef.value.getBoundingClientRect()
  const mx = e.clientX - rect.left
  const my = e.clientY - rect.top
  const scale = getScale()
  const drawW = img.naturalWidth * scale
  const drawH = img.naturalHeight * scale
  const offsetX = (canvasSize - drawW) / 2
  const offsetY = (canvasSize - drawH) / 2

  const cx = offsetX + cropX.value * scale
  const cy = offsetY + cropY.value * scale
  const cw = cropW.value * scale
  const ch = cropH.value * scale

  // 检查是否点击了四角（缩放）
  if (isNearCorner(mx, my, cx + cw, cy + ch)) {
    resizing = true
    dragStartX = mx
    dragStartY = my
    dragStartCropW = cropW.value
    dragStartCropH = cropH.value
  } else if (mx >= cx && mx <= cx + cw && my >= cy && my <= cy + ch) {
    // 在裁剪框内（拖拽）
    dragging = true
    dragStartX = mx
    dragStartY = my
    dragStartCropX = cropX.value
    dragStartCropY = cropY.value
  }
}

function onMouseMove(e) {
  if (!dragging && !resizing) return
  const rect = canvasRef.value.getBoundingClientRect()
  const mx = e.clientX - rect.left
  const my = e.clientY - rect.top
  const scale = getScale()

  if (dragging) {
    const dx = (mx - dragStartX) / scale
    const dy = (my - dragStartY) / scale
    cropX.value = Math.round(
      Math.max(0, Math.min(img.naturalWidth - cropW.value,
        dragStartCropX + dx)))
    cropY.value = Math.round(
      Math.max(0, Math.min(img.naturalHeight - cropH.value,
        dragStartCropY + dy)))
  }

  if (resizing) {
    const dx = (mx - dragStartX) / scale
    const dy = (my - dragStartY) / scale
    const newSize = Math.round(
      Math.max(MIN_CROP,
        Math.min(img.naturalWidth - cropX.value,
          img.naturalHeight - cropY.value,
          Math.max(dragStartCropW + dx, dragStartCropH + dy))))
    cropW.value = newSize
    cropH.value = newSize
  }

  draw()
}

function onMouseUp() {
  if (dragging || resizing) {
    emitCrop()
  }
  dragging = false
  resizing = false
}

function emitCrop() {
  emit('crop-change', {
    x: cropX.value,
    y: cropY.value,
    width: cropW.value,
    height: cropH.value
  })
}
</script>

<style scoped>
.image-cropper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.cropper-canvas-wrapper {
  position: relative;
  width: 400px;
  height: 400px;
  border: 1px solid #ddd;
  border-radius: 8px;
  overflow: hidden;
  background: #f0f0f0;
}
.cropper-canvas-wrapper canvas {
  cursor: crosshair;
  display: block;
}
.upload-hint {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #999;
  font-size: 14px;
}
.crop-info {
  font-size: 12px;
  color: #666;
}
</style>
```

- [ ] **Step 2: 验证前端构建**

```bash
cd digital-human-web
npm run build
```
Expected: 构建成功

- [ ] **Step 3: Commit**

```bash
git add digital-human-web/src/components/ImageCropper.vue
git commit -m "feat: add ImageCropper component with drag-to-crop face selection"
```

---

### Task 11: API 封装 + Pinia Store 更新

**Files:**
- Modify: `digital-human-web/src/services/api.js`
- Modify: `digital-human-web/src/stores/avatar.js`

- [ ] **Step 1: 在 api.js 中新增 avatar API**

在 `digital-human-web/src/services/api.js` 末尾追加：

```javascript
// ---- 形象管理 API ----

export function uploadAvatar(imageFile, name, cropX, cropY, cropW, cropH) {
  const formData = new FormData()
  formData.append('image', imageFile)
  formData.append('name', name)
  formData.append('cropX', cropX)
  formData.append('cropY', cropY)
  formData.append('cropW', cropW)
  formData.append('cropH', cropH)
  return api.post('/admin/avatar/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function listAdminAvatars() {
  return api.get('/admin/avatar/list')
}

export function deleteAdminAvatar(id) {
  return api.delete(`/admin/avatar/${id}`)
}

export function listAvatars() {
  return api.get('/avatar/list')
}

export function switchSessionAvatar(sessionId, avatarId) {
  return api.put(`/session/${sessionId}/avatar`, { avatarId })
}
```

**注意**：管理端 API（`/admin/avatar/*`）需要 Basic Auth。需要在 axios 请求中携带 auth header。更新 `api.js` 顶部的 `api` 创建部分，或者为 admin 请求单独创建实例。这里需要确认现有的 admin 认证如何传递。

查看现有 AdminPanel.vue，发现它使用独立的 `axios.create({ baseURL: '/api/admin' })`。保持一致性，管理端 API 仍使用 admin 的 axios 实例。

更新 api.js，为 admin API 创建独立实例：

在 `api.js` 中，在现有 `export default api` 之前添加：

```javascript
const adminApi = axios.create({
  baseURL: '/api/admin',
  timeout: 30000
})

// 携带 Basic Auth（与 AdminAuthFilter 匹配）
adminApi.interceptors.request.use(config => {
  config.headers.Authorization = 'Basic ' + btoa('admin:dhAdmin2024')
  return config
})

export { adminApi }
```

然后管理端的 avatar 函数使用 `adminApi`：

```javascript
export function uploadAvatar(imageFile, name, cropX, cropY, cropW, cropH) {
  const formData = new FormData()
  formData.append('image', imageFile)
  formData.append('name', name)
  formData.append('cropX', cropX)
  formData.append('cropY', cropY)
  formData.append('cropW', cropW)
  formData.append('cropH', cropH)
  return adminApi.post('/avatar/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function listAdminAvatars() {
  return adminApi.get('/avatar/list')
}

export function deleteAdminAvatar(id) {
  return adminApi.delete(`/avatar/${id}`)
}
```

- [ ] **Step 2: 更新 avatar.js store**

完整重写 `digital-human-web/src/stores/avatar.js`：

```javascript
import { defineStore } from 'pinia'
import {
  uploadAvatar,
  listAdminAvatars,
  deleteAdminAvatar,
  listAvatars,
  switchSessionAvatar
} from '../services/api'
import { useSessionStore } from './session'

export const useAvatarStore = defineStore('avatar', {
  state: () => ({
    modelPath: '/models/base/haru.model3.json',
    avatars: [],
    defaultId: '',
    currentAvatarId: '',
    uploading: false
  }),

  actions: {
    /** 管理员：上传并生成新形象 */
    async uploadAvatar(imageFile, name, cropX, cropY, cropW, cropH) {
      this.uploading = true
      try {
        const { data } = await uploadAvatar(imageFile, name, cropX, cropY, cropW, cropH)
        if (data.code === 200) {
          await this.loadAdminAvatars()
          return data.data
        }
        throw new Error(data.message || '上传失败')
      } finally {
        this.uploading = false
      }
    },

    /** 管理员：加载形象库列表 */
    async loadAdminAvatars() {
      const { data } = await listAdminAvatars()
      if (data.code === 200) {
        this.avatars = data.data.avatars || []
        this.defaultId = data.data.defaultId || ''
      }
    },

    /** 管理员：删除形象 */
    async deleteAvatar(id) {
      const { data } = await deleteAdminAvatar(id)
      if (data.code === 200) {
        await this.loadAdminAvatars()
      }
      return data
    },

    /** 用户端：加载可用形象列表 */
    async loadAvatars() {
      const { data } = await listAvatars()
      if (data.code === 200) {
        this.avatars = data.data.avatars || []
        this.defaultId = data.data.defaultId || ''
        if (!this.currentAvatarId && this.defaultId) {
          this.currentAvatarId = this.defaultId
          const def = this.avatars.find(a => a.id === this.defaultId)
          if (def) this.modelPath = def.modelPath
        }
      }
    },

    /** 用户端：切换当前会话形象 */
    async switchAvatar(avatarId) {
      const sessionStore = useSessionStore()
      if (!sessionStore.sessionId) return

      const { data } = await switchSessionAvatar(sessionStore.sessionId, avatarId)
      if (data.code === 200) {
        this.currentAvatarId = avatarId
        this.modelPath = data.data.modelPath
      }
      return data
    },

    setModelPath(path) {
      this.modelPath = path
    }
  }
})
```

- [ ] **Step 3: 验证前端构建**

```bash
cd digital-human-web
npm run build
```
Expected: 构建成功

- [ ] **Step 4: Commit**

```bash
git add digital-human-web/src/services/api.js
git add digital-human-web/src/stores/avatar.js
git commit -m "feat: add avatar API functions and Pinia store actions"
```

---

### Task 12: AdminPanel.vue — 集成裁剪+上传流程

**Files:**
- Modify: `digital-human-web/src/components/AdminPanel.vue`

- [ ] **Step 1: 在 AdminPanel 中新增"形象管理"区块**

在 AdminPanel.vue 的 `<template>` 中，在 `</el-form>` 之前（即 TTS 配置的最后一个 `</el-form-item>` 之后、`</el-form>` 之前）添加以下区块：

```vue
      <el-divider />
      <h3>数字人形象管理</h3>

      <!-- 上传生成新形象 -->
      <el-form-item label="形象名称">
        <el-input v-model="avatarName" placeholder="例如：知性女助理" style="width: 260px" />
      </el-form-item>
      <el-form-item label="选择图片">
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          :on-change="onImageSelected"
          accept="image/png,image/jpeg"
        >
          <el-button type="primary" :disabled="avatarStore.uploading">
            选择二次元图片
          </el-button>
        </el-upload>
      </el-form-item>
      <el-form-item v-if="selectedImage" label="框选面部">
        <ImageCropper
          :imageFile="selectedImage"
          @crop-change="onCropChange"
        />
      </el-form-item>
      <el-form-item v-if="cropData">
        <el-button
          type="success"
          @click="generateAvatar"
          :loading="avatarStore.uploading"
          :disabled="!avatarName.trim()"
        >
          开始生成形象
        </el-button>
      </el-form-item>

      <!-- 形象库 -->
      <el-divider />
      <h4>形象库（共 {{ avatarStore.avatars.length }} 个）</h4>
      <el-form-item>
        <div class="avatar-gallery">
          <div
            v-for="av in avatarStore.avatars"
            :key="av.id"
            class="avatar-card"
          >
            <img :src="av.thumbnailPath" class="avatar-thumb" />
            <div class="avatar-name">{{ av.name }}</div>
            <el-button
              size="small"
              type="danger"
              @click="deleteAvatar(av.id)"
              :disabled="av.id === avatarStore.defaultId"
            >
              删除
            </el-button>
          </div>
          <div v-if="avatarStore.avatars.length === 0" class="no-avatar">
            暂无形象，请上传生成
          </div>
        </div>
      </el-form-item>
```

- [ ] **Step 2: 在 `<script setup>` 中新增逻辑**

在 `AdminPanel.vue` 的 `<script setup>` 中添加：

```javascript
import { ref } from 'vue'
import ImageCropper from './ImageCropper.vue'
import { useAvatarStore } from '../stores/avatar'

const avatarStore = useAvatarStore()
const avatarName = ref('')
const selectedImage = ref(null)
const cropData = ref(null)

function onImageSelected(uploadFile) {
  selectedImage.value = uploadFile.raw
  cropData.value = null
}

function onCropChange(data) {
  cropData.value = data
}

async function generateAvatar() {
  if (!cropData.value || !avatarName.value.trim()) return
  try {
    await avatarStore.uploadAvatar(
      selectedImage.value,
      avatarName.value.trim(),
      cropData.value.x,
      cropData.value.y,
      cropData.value.width,
      cropData.value.height
    )
    // 重置
    selectedImage.value = null
    cropData.value = null
    avatarName.value = ''
    ElMessage.success('形象生成成功！')
  } catch (e) {
    ElMessage.error('生成失败: ' + (e.message || '未知错误'))
  }
}

async function deleteAvatar(id) {
  try {
    await avatarStore.deleteAvatar(id)
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

import { ElMessage } from 'element-plus'

// 初始化加载
import { onMounted } from 'vue'
onMounted(() => {
  avatarStore.loadAdminAvatars()
})
```

- [ ] **Step 3: 添加样式**

在 `<style scoped>` 中添加：

```css
.avatar-gallery {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.avatar-card {
  width: 140px;
  text-align: center;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 8px;
}
.avatar-thumb {
  width: 120px;
  height: 120px;
  object-fit: cover;
  border-radius: 4px;
  background: #f5f5f5;
}
.avatar-name {
  font-size: 13px;
  margin: 6px 0;
  color: #333;
}
.no-avatar {
  color: #999;
  font-size: 14px;
}
```

- [ ] **Step 4: 验证构建**

```bash
cd digital-human-web
npm run build
```
Expected: 构建成功

- [ ] **Step 5: Commit**

```bash
git add digital-human-web/src/components/AdminPanel.vue
git commit -m "feat: integrate ImageCropper and avatar management into AdminPanel"
```

---

### Task 13: ChatView + Live2DCanvas — 用户端形象切换

**Files:**
- Modify: `digital-human-web/src/components/ChatView.vue`
- Modify: `digital-human-web/src/components/Live2DCanvas.vue`

- [ ] **Step 1: 更新 Live2DCanvas.vue 支持动态切换 modelPath**

在 `Live2DCanvas.vue` 中，添加对 `modelPath` prop 变化的监听，当路径变化时重新初始化模型：

在 `<script setup>` 的现有 watch 之后添加：

```javascript
// 监听 modelPath 变化，动态切换模型
watch(() => props.modelPath, async (newPath) => {
  if (newPath && canvasRef.value) {
    driver.destroy()
    await driver.init(canvasRef.value, newPath)
  }
})
```

（`watch` 已从 vue 导入，无需重复添加 import）

- [ ] **Step 2: 更新 ChatView.vue 新增形象切换菜单**

在 `ChatView.vue` 的 `<template>` 中，在 `.main-stage` div 内，`<Live2DCanvas>` 上方添加切换按钮：

```vue
<template>
  <div class="chat-view">
    <div class="main-stage">
      <!-- 形象切换按钮 -->
      <div class="avatar-switcher">
        <el-dropdown @command="onAvatarSwitch" trigger="click">
          <el-button type="default" size="small">
            🎭 切换形象
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="av in avatarStore.avatars"
                :key="av.id"
                :command="av.id"
                :class="{ 'is-active': av.id === avatarStore.currentAvatarId }"
              >
                {{ av.name }}{{ av.id === avatarStore.currentAvatarId ? ' ✓' : '' }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <Live2DCanvas
        :width="600"
        :height="600"
        :modelPath="avatarStore.modelPath"
      />
    </div>
    <ChatPanel />
    <InputBar />
  </div>
</template>
```

- [ ] **Step 3: 在 ChatView.vue 的 `<script setup>` 中新增逻辑**

在 script 中添加：

```javascript
import { useAvatarStore } from '../stores/avatar'

const avatarStore = useAvatarStore()

function onAvatarSwitch(avatarId) {
  avatarStore.switchAvatar(avatarId)
}

onMounted(async () => {
  await sessionStore.initSession()
  signaling.connect(sessionStore.sessionId)
  // 加载可用形象列表
  await avatarStore.loadAvatars()
})
```

- [ ] **Step 4: 添加样式**

在 `<style scoped>` 中添加：

```css
.avatar-switcher {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 100;
}
.avatar-switcher .el-button {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(4px);
}
```

- [ ] **Step 5: 验证构建**

```bash
cd digital-human-web
npm run build
```
Expected: 构建成功

- [ ] **Step 6: Commit**

```bash
git add digital-human-web/src/components/ChatView.vue
git add digital-human-web/src/components/Live2DCanvas.vue
git commit -m "feat: add avatar switcher in ChatView and dynamic model reload in Live2DCanvas"
```

---

## 里程碑 4：基础设施

### Task 14: Live2D 基础模型部署

**Files:**
- Create: `digital-human-web/public/models/base/` （目录及模型文件）
- Modify: `digital-human-server/src/main/resources/application.yml` （确认 face-region 坐标）

- [ ] **Step 1: 下载 Live2D Cubism SDK for Web**

从 [Live2D 官网](https://www.live2d.com/download/cubism-sdk/) 下载 Cubism SDK for Web。

- [ ] **Step 2: 提取 Haru 示例模型**

解压 SDK 后，从 `Samples/Resources/Haru/` 复制所有文件到 `digital-human-web/public/models/base/`。

```bash
# 示例命令（需根据实际下载路径调整）
cp -r ~/Downloads/CubismSdkForWeb-5-r.1/Samples/Resources/Haru/* \
      digital-human-web/public/models/base/
```

目标结构：

```
digital-human-web/public/models/base/
├── haru.model3.json
├── haru.physics3.json
├── haru.pose3.json
├── haru.cdi3.json
├── haru.2048/
│   └── texture_00.png
├── haru.motion3.json
└── expressions/
    ├── happy.exp3.json
    ├── sad.exp3.json
    └── ...
```

- [ ] **Step 3: 确定面部区域坐标**

打开 `haru.2048/texture_00.png`，用图片查看器（如 PhotoShop、GIMP、或系统自带）确定面部在纹理图中的矩形坐标 (x, y, width, height)。

更新 `application.yml` 中的 `app.avatar.face-region` 为实际坐标值。

- [ ] **Step 4: 创建初始默认形象**

手动创建第一个默认形象目录：

```bash
mkdir -p digital-human-web/public/models/generated/avatar_default
cp -r digital-human-web/public/models/base/* \
      digital-human-web/public/models/generated/avatar_default/
echo '{"id":"avatar_default","name":"默认形象","createdAt":"2026-06-09T00:00:00"}' \
  > digital-human-web/public/models/generated/avatar_default/meta.json
```

- [ ] **Step 5: 更新默认 modelPath 配置**

更新 `application.yml` 和 `t_dh_config` 表中的 `live2d_model_path` 为：

```
/models/generated/avatar_default/haru.model3.json
```

- [ ] **Step 6: Commit**

```bash
git add digital-human-web/public/models/
git commit -m "chore: deploy Live2D base model (Haru sample) and default avatar"
```

---

## 任务依赖关系

```
Task 1 (Config) ─────────────────────────────────────────────┐
    │                                                         │
    ├── Task 2 (TextureService) ── Task 3 (TextureServiceTest) │
    │         │                                                 │
    │         ├── Task 4 (ModelFileService) ── Task 5 (Test)    │
    │         │         │                                       │
    │         │         └── Task 6 (AvatarService) ── Task 7    │
    │         │                   │                             │
    │         │                   ├── Task 8 (AdminController)   │
    │         │                   │                              │
    │         │                   └── Task 9 (Public APIs)       │
    │         │                                                  │
    └─────────┴────────────────────────────────────────────────┘
                              │
                    Task 10 (ImageCropper)
                              │
                    Task 11 (api.js + store)
                              │
              ┌───────────────┴───────────────┐
              │                               │
    Task 12 (AdminPanel)        Task 13 (ChatView + Canvas)
              │                               │
              └───────────────┬───────────────┘
                              │
                    Task 14 (Model Deploy)
```

里程碑 1-2（后端）可以按 Task 1→2→4→6→8→9 顺序执行，测试穿插在对应实现之后。里程碑 3（前端）可以按 Task 10→11→12→13 顺序执行。

---

## 验证清单

完成所有任务后，执行以下验证：

1. **后端单元测试全部通过**：
   ```bash
   cd digital-human-server
   mvn test
   ```

2. **前端构建成功**：
   ```bash
   cd digital-human-web
   npm run build
   ```

3. **端到端测试**：
   - 启动服务端 `mvn spring-boot:run`
   - 访问管理后台 → 上传一张二次元图片 → 框选面部 → 生成
   - 访问对话页面 → 点击"切换形象" → 选择新生成的形象 → Live2D 渲染更新
   - 切换回默认形象 → 正常显示
