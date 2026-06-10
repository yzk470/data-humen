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
        BufferedImage base = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = base.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 512, 512);
        g.dispose();
        baseTexture = toPngBytes(base);
        faceRegion = new Rectangle(128, 128, 256, 256);
    }

    @Test
    void shouldReplaceFaceRegionSuccessfully() throws IOException {
        BufferedImage source = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 512, 512);
        g.dispose();

        byte[] result = textureService.replaceFaceRegion(
            toPngBytes(source),
            64, 64, 256, 256,
            baseTexture,
            faceRegion
        );

        assertNotNull(result);
        assertTrue(result.length > 0);

        BufferedImage resultImg = ImageIO.read(new java.io.ByteArrayInputStream(result));
        int centerPixel = resultImg.getRGB(256, 256);
        int blue = centerPixel & 0xFF;
        assertTrue(blue > 100, "面部区域中心应该偏蓝，实际 blue=" + blue);

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
                100, 100, 256, 256,
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
                10, 10, 32, 32,
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
