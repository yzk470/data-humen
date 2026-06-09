package com.dh.server.connector;

import com.dh.server.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class QwenVLConnectorTest {

    private QwenVLConnector connector;
    private AppConfig appConfig;

    @BeforeEach
    void setUp() {
        appConfig = new AppConfig();
        appConfig.getQwenVl().setApiKey("test-key");
        appConfig.getQwenVl().setApiUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        appConfig.getQwenVl().setModel("qwen-vl-max");
        connector = new QwenVLConnector(appConfig);
    }

    @Test
    void shouldConstructSuccessfully() {
        assertNotNull(connector);
    }

    @Test
    void shouldFailGracefullyWithInvalidApiKey() {
        byte[] imageBytes = new byte[]{0x01, 0x02, 0x03};
        RuntimeException e = assertThrows(RuntimeException.class, () -> {
            connector.detectFace(imageBytes);
        });
        assertTrue(e.getMessage().contains("AI 识别失败"));
    }

    @Test
    void shouldCreateFaceRegionCorrectly() {
        QwenVLConnector.FaceRegion region = new QwenVLConnector.FaceRegion();
        region.setX(100);
        region.setY(50);
        region.setWidth(200);
        region.setHeight(200);
        assertEquals(100, region.getX());
        assertEquals(50, region.getY());
        assertEquals(200, region.getWidth());
        assertEquals(200, region.getHeight());
    }

    @Test
    @Disabled("需要有效的 QWEN_VL_API_KEY")
    void shouldDetectFaceWithValidApiKey() throws IOException {
        appConfig.getQwenVl().setApiKey(System.getenv("QWEN_VL_API_KEY"));
        BufferedImage img = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.PINK);
        g.fillOval(156, 106, 200, 300);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);

        QwenVLConnector.FaceRegion region = connector.detectFace(baos.toByteArray());
        assertNotNull(region);
        assertTrue(region.getWidth() > 0);
        assertTrue(region.getHeight() > 0);
    }
}
