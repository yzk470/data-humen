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
    private QwenVL qwenVl = new QwenVL();

    @Data
    public static class Avatar {
        private String baseModel = "models/base/Haru.model3.json";
        private FaceRegion faceRegion = new FaceRegion();
        private String modelsDir = "../digital-human-web/public/models";

        @Data
        public static class FaceRegion {
            private int x = 577;
            private int y = 13;
            private int width = 209;
            private int height = 255;
        }
    }

    @Data
    public static class QwenVL {
        private String apiKey;
        private String apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String model = "qwen-vl-max";
    }
}
