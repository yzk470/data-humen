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

        verify(modelFileService, never()).createModel(anyString(), anyString(),
            any(), anyString());
    }
}
