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
        byte[] baseTexture = modelFileService.loadBaseTexture();

        AppConfig.Avatar.FaceRegion fr = appConfig.getAvatar().getFaceRegion();
        Rectangle faceRegion = new Rectangle(fr.getX(), fr.getY(),
            fr.getWidth(), fr.getHeight());
        byte[] newTexture = textureService.replaceFaceRegion(
            imageBytes, cropX, cropY, cropW, cropH,
            baseTexture, faceRegion
        );

        String avatarId = "avatar_" + System.currentTimeMillis();
        modelFileService.createModel(avatarId, name, newTexture, "texture_00.png");

        return modelFileService.getAvatarInfo(avatarId);
    }
}
