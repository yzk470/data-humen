package com.dh.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {
    private DeepSeek deepseek = new DeepSeek();

    @Data
    public static class DeepSeek {
        private String apiKey;
        private String apiUrl = "https://api.deepseek.com/v1/chat/completions";
        private String model = "deepseek-chat";
    }

    private Avatar avatar = new Avatar();

    @Data
    public static class Avatar {
        /** 基础 Live2D 模型 JSON 路径，相对于 modelsRoot */
        private String baseModel = "models/base/Haru.model3.json";
        /** 面部在纹理图集中的矩形区域 */
        private FaceRegion faceRegion = new FaceRegion();
        /** 模型文件根目录 */
        private String modelsDir = "../digital-human-web/public/models";

        @Data
        public static class FaceRegion {
            private int x = 680;
            private int y = 430;
            private int width = 160;
            private int height = 160;
        }
    }
}
