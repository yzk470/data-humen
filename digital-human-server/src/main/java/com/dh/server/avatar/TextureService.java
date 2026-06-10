package com.dh.server.avatar;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Service
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
            BufferedImage source = readImage(sourceImage);
            validateCropBounds(source, cropX, cropY, cropW, cropH);
            BufferedImage cropped = source.getSubimage(cropX, cropY, cropW, cropH);

            BufferedImage scaled = resizeToFit(cropped,
                (int) faceRegion.getWidth(), (int) faceRegion.getHeight());

            BufferedImage base = readImage(baseTexture);
            Graphics2D g = base.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(scaled, (int) faceRegion.getX(), (int) faceRegion.getY(), null);
            g.dispose();

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

    private BufferedImage resizeToFit(BufferedImage source, int targetW, int targetH) {
        double scale = Math.max(
            (double) targetW / source.getWidth(),
            (double) targetH / source.getHeight()
        );
        int scaledW = (int) (source.getWidth() * scale);
        int scaledH = (int) (source.getHeight() * scale);

        BufferedImage scaled = new BufferedImage(scaledW, scaledH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source, 0, 0, scaledW, scaledH, null);
        g.dispose();

        BufferedImage result = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        int offsetX = (targetW - scaledW) / 2;
        int offsetY = (targetH - scaledH) / 2;
        g2.drawImage(scaled, offsetX, offsetY, null);
        g2.dispose();

        return result;
    }

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
