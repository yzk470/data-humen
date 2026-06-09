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

        Path baseModelDir = tempDir.resolve("base");
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
        BufferedImage newTex = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = newTex.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 256, 256);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(newTex, "PNG", baos);

        String modelPath = modelFileService.createModel(
            "avatar_test_001", "测试形象", baos.toByteArray(), "texture_00.png");

        assertNotNull(modelPath);
        assertTrue(modelPath.contains("avatar_test_001"));
        assertTrue(modelPath.endsWith(".model3.json"));

        Path genDir = tempDir.resolve("generated/avatar_test_001");
        assertTrue(Files.exists(genDir.resolve("haru.model3.json")));
        assertTrue(Files.exists(genDir.resolve("meta.json")));
        assertTrue(Files.exists(genDir.resolve("thumbnail.png")));

        String meta = Files.readString(genDir.resolve("meta.json"));
        assertTrue(meta.contains("测试形象"));
        assertTrue(meta.contains("avatar_test_001"));

        List<ModelFileService.AvatarInfo> avatars = modelFileService.listAvatars();
        assertEquals(1, avatars.size());
        assertEquals("测试形象", avatars.get(0).getName());
    }

    @Test
    void shouldDeleteAvatar() throws IOException {
        byte[] texBytes = createTestTexture();
        modelFileService.createModel("avatar_del", "待删除", texBytes, "texture_00.png");

        assertEquals(1, modelFileService.listAvatars().size());

        modelFileService.deleteAvatar("avatar_del");

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
