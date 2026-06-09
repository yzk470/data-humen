package com.dh.server.avatar;

import com.dh.server.config.AppConfig;
import com.dh.server.connector.TextureFusionConnector;
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
public class AvatarService {

    private final TextureService textureService;
    private final TextureFusionConnector fusionConnector;
    private final ModelFileService modelFileService;
    private final AppConfig appConfig;

    public AvatarService(TextureService textureService,
                          TextureFusionConnector fusionConnector,
                          ModelFileService modelFileService,
                          AppConfig appConfig) {
        this.textureService = textureService;
        this.fusionConnector = fusionConnector;
        this.modelFileService = modelFileService;
        this.appConfig = appConfig;
    }

    /**
     * 换皮流水线：
     *   二次元图片 → 裁剪 → [AI融合 or 机械合成] → 保存模型
     */
    public ModelFileService.AvatarInfo generateAvatar(byte[] imageBytes, String name,
                                                       int cropX, int cropY,
                                                       int cropW, int cropH)
            throws IOException {
        // 1. 裁剪面部
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
        BufferedImage cropped = source.getSubimage(cropX, cropY, cropW, cropH);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(cropped, "PNG", baos);
        byte[] faceBytes = baos.toByteArray();

        // 2. 读取基础纹理
        byte[] baseTexture = modelFileService.loadBaseTexture();

        // 3. 纹理合成（优先 AI 融合，失败则机械合成）
        byte[] newTexture;
        try {
            log.info("尝试 AI 纹理融合...");
            newTexture = fusionConnector.fuseFace(faceBytes, baseTexture, name);
            log.info("AI 纹理融合成功");
        } catch (Exception e) {
            log.warn("AI 融合失败，降级为机械合成: {}", e.getMessage());
            AppConfig.Avatar.FaceRegion fr = appConfig.getAvatar().getFaceRegion();
            Rectangle faceRegion = new Rectangle(fr.getX(), fr.getY(),
                fr.getWidth(), fr.getHeight());
            newTexture = textureService.replaceFaceRegion(
                imageBytes, cropX, cropY, cropW, cropH,
                baseTexture, faceRegion
            );
        }

        // 4. 创建模型文件
        String avatarId = "avatar_" + System.currentTimeMillis();
        modelFileService.createModel(avatarId, name, newTexture, "texture_00.png");

        return modelFileService.getAvatarInfo(avatarId);
    }
}
