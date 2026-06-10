package com.dh.server.connector;

import com.dh.server.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
public class QwenVLConnector {

    private final AppConfig appConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public QwenVLConnector(AppConfig appConfig) {
        this.appConfig = appConfig;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Data
    public static class FaceRegion {
        private int x;
        private int y;
        private int width;
        private int height;
    }

    /**
     * 调用通义千问 VL 识别图片中的面部区域。
     */
    public FaceRegion detectFace(byte[] imageBytes) {
        long startTime = System.currentTimeMillis();
        try {
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            String imageUrl = "data:image/png;base64," + base64;

            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("type", "image_url");
            imagePart.put("image_url", Map.of("url", imageUrl));
            content.add(imagePart);

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", "Detect the center coordinates of both eyes and mouth of the anime character. Return ONLY a JSON object with pixel coordinates: {\"eyeLX\":100,\"eyeLY\":200,\"eyeRX\":300,\"eyeRY\":200,\"mouthX\":200,\"mouthY\":350}. Each value is an integer pixel coordinate relative to the image top-left corner.");
            content.add(textPart);

            Map<String, Object> body = new HashMap<>();
            body.put("model", appConfig.getQwenVl().getModel());
            body.put("messages", List.of(Map.of("role", "user", "content", content)));
            body.put("temperature", 0.1);
            body.put("max_tokens", 200);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(appConfig.getQwenVl().getApiKey());

            String requestBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                appConfig.getQwenVl().getApiUrl() + "/chat/completions",
                HttpMethod.POST,
                entity,
                String.class
            );

            log.info("QwenVL 响应: {} (耗时 {}ms)", response.getBody(),
                System.currentTimeMillis() - startTime);

            String replyText = extractReplyText(response.getBody());
            FaceRegion region = parseFaceRegion(replyText);
            region = clampToImage(region);
            return region;

        } catch (Exception e) {
            log.error("QwenVL API 调用失败", e);
            throw new RuntimeException("AI 识别失败: " + e.getMessage());
        }
    }

    private String extractReplyText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("choices").get(0)
            .path("message").path("content").asText();
    }

    private FaceRegion parseFaceRegion(String text) throws Exception {
        String cleaned = text.trim();
        log.info("QwenVL 原始回复: {}", cleaned);
        cleaned = cleaned.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        cleaned = cleaned.replace("，", ",").replaceAll("\\s+", "");
        log.info("QwenVL 清理后: {}", cleaned);

        // 尝试从五官坐标计算面部区域
        try {
            int eyeLX = extractInt(cleaned, "eyeLX");
            int eyeLY = extractInt(cleaned, "eyeLY");
            int eyeRX = extractInt(cleaned, "eyeRX");
            int eyeRY = extractInt(cleaned, "eyeRY");
            int mouthX = extractInt(cleaned, "mouthX");
            int mouthY = extractInt(cleaned, "mouthY");

            int eyeCX = (eyeLX + eyeRX) / 2;
            int eyeCY = (eyeLY + eyeRY) / 2;
            int eyeDist = Math.abs(eyeRX - eyeLX);
            int eyeToMouth = mouthY - eyeCY;

            // 二次元面部正方形：眼睛间距*1.3 or 眼嘴距*1.5，取较小值
            int size = (int)(Math.min(eyeDist * 1.3, eyeToMouth * 1.5));
            // 保证最小尺寸
            size = Math.max(size, 100);
            int fx = eyeCX - size / 2;
            int fy = eyeCY - (int)(eyeToMouth * 0.35);

            FaceRegion region = new FaceRegion();
            region.setX(Math.max(0, fx));
            region.setY(Math.max(0, fy));
            region.setWidth(size);
            region.setHeight(size);
            return region;
        } catch (Exception e) {
            log.info("五官检测失败，尝试直接坐标: {}", e.getMessage());
        }

        // 兜底: 直接提取 x/y/width/height
        int x = extractInt(cleaned, "x");
        int y = extractInt(cleaned, "y");
        int width = extractInt(cleaned, "width");
        int height = extractInt(cleaned, "height");

        FaceRegion region = new FaceRegion();
        region.setX(x);
        region.setY(y);
        region.setWidth(width);
        region.setHeight(height);
        return region;
    }

    private int extractInt(String text, String key) throws Exception {
        // 尝试多种格式: "x":180, x:180, "x": 180
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("[\"']?" + key + "[\"']?\\s*[:：]\\s*(\\d+)")
            .matcher(text);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        throw new Exception("无法从回复中提取字段: " + key + ", 回复内容: " + text);
    }

    private FaceRegion clampToImage(FaceRegion r) {
        if (r.x < 0) r.x = 0;
        if (r.y < 0) r.y = 0;
        if (r.width < 64) r.width = 128;
        if (r.height < 64) r.height = 128;
        return r;
    }
}
