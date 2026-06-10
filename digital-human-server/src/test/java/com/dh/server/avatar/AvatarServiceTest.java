package com.dh.server.avatar;

import com.dh.server.config.AppConfig;
import com.dh.server.connector.TextureFusionConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AvatarServiceTest {

    private TextureService textureService;
    private TextureFusionConnector fusionConnector;
    private ModelFileService modelFileService;
    private AppConfig appConfig;
    private AvatarService avatarService;

    @BeforeEach
    void setUp() {
        textureService = mock(TextureService.class);
        fusionConnector = mock(TextureFusionConnector.class);
        modelFileService = mock(ModelFileService.class);
        appConfig = new AppConfig();
        appConfig.getAvatar().getFaceRegion().setX(512);
        appConfig.getAvatar().getFaceRegion().setY(128);
        appConfig.getAvatar().getFaceRegion().setWidth(256);
        appConfig.getAvatar().getFaceRegion().setHeight(256);
        avatarService = new AvatarService(textureService, fusionConnector,
            modelFileService, appConfig);
    }

    private byte[] createTestPng() throws IOException {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
            200, 200, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    @Test
    void shouldCompleteAIFusionPipeline() throws IOException {
        byte[] image = createTestPng();
        byte[] baseTex = new byte[]{0x03, 0x04};
        byte[] fusedTex = new byte[]{0x05, 0x06};

        when(modelFileService.loadBaseTexture()).thenReturn(baseTex);
        when(fusionConnector.fuseFace(any(), eq(baseTex), eq("测试")))
            .thenReturn(fusedTex);

        ModelFileService.AvatarInfo expectedInfo = new ModelFileService.AvatarInfo();
        expectedInfo.setId("avatar_123");
        expectedInfo.setName("测试");
        expectedInfo.setModelPath("/models/generated/avatar_123/haru.model3.json");
        expectedInfo.setThumbnailPath("/models/generated/avatar_123/thumbnail.png");
        expectedInfo.setCreatedAt("2026-06-09T12:00:00");

        when(modelFileService.getAvatarInfo(anyString())).thenReturn(expectedInfo);

        ModelFileService.AvatarInfo result = avatarService.generateAvatar(
            image, "测试", 10, 10, 100, 100);

        assertNotNull(result);
        assertEquals("测试", result.getName());
        assertEquals("/models/generated/avatar_123/haru.model3.json", result.getModelPath());

        verify(fusionConnector).fuseFace(any(), eq(baseTex), eq("测试"));
        verify(modelFileService).createModel(anyString(), eq("测试"),
            eq(fusedTex), eq("texture_00.png"));
    }

    @Test
    void shouldFallbackToTextureServiceWhenFusionFails() throws IOException {
        byte[] image = createTestPng();
        byte[] baseTex = new byte[]{0x03, 0x04};
        byte[] fallbackTex = new byte[]{0x07, 0x08};

        when(modelFileService.loadBaseTexture()).thenReturn(baseTex);
        when(fusionConnector.fuseFace(any(), any(), any()))
            .thenThrow(new RuntimeException("AI 融合失败"));
        when(textureService.replaceFaceRegion(eq(image),
            eq(10), eq(10), eq(100), eq(100), eq(baseTex), any()))
            .thenReturn(fallbackTex);

        ModelFileService.AvatarInfo expectedInfo = new ModelFileService.AvatarInfo();
        expectedInfo.setId("avatar_fallback");
        expectedInfo.setName("fallback");
        expectedInfo.setModelPath("/models/fallback.model3.json");
        when(modelFileService.getAvatarInfo(anyString())).thenReturn(expectedInfo);

        ModelFileService.AvatarInfo result = avatarService.generateAvatar(
            image, "fallback", 10, 10, 100, 100);

        assertNotNull(result);
        assertEquals("fallback", result.getName());

        verify(fusionConnector).fuseFace(any(), any(), any());
        verify(textureService).replaceFaceRegion(eq(image),
            eq(10), eq(10), eq(100), eq(100), eq(baseTex), any());
        verify(modelFileService).createModel(anyString(), eq("fallback"),
            eq(fallbackTex), eq("texture_00.png"));
    }
}
