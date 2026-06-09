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
            textPart.put("text", "识别图中人物的面部区域。仅返回JSON对象，格式为：{\"x\":数字,\"y\":数字,\"width\":数字,\"height\":数字}。x和y是面部左上角相对于图片左上角的像素坐标，width和height是面部的像素宽高。不要返回任何其他文字。");
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
        String json = text.trim();
        json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        JsonNode node = objectMapper.readTree(json);
        FaceRegion region = new FaceRegion();
        region.setX(node.path("x").asInt());
        region.setY(node.path("y").asInt());
        region.setWidth(node.path("width").asInt());
        region.setHeight(node.path("height").asInt());
        return region;
    }

    private FaceRegion clampToImage(FaceRegion r) {
        if (r.x < 0) r.x = 0;
        if (r.y < 0) r.y = 0;
        if (r.width < 64) r.width = 128;
        if (r.height < 64) r.height = 128;
        return r;
    }
}
