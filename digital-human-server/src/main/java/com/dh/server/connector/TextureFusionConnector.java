package com.dh.server.connector;

import com.dh.server.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
public class TextureFusionConnector {

    private final AppConfig appConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TextureFusionConnector(AppConfig appConfig) {
        this.appConfig = appConfig;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 分两步：
     * 1. 先调用 QwenVL 分析两张图，生成融合后的图片描述
     * 2. 调用通义万象文生图 API，根据描述生成最终纹理
     */
    public byte[] fuseFace(byte[] faceImage, byte[] baseTexture, String faceDesc) {
        long start = System.currentTimeMillis();
        try {
            // Step 1: 用 QwenVL 生成融合描述
            String prompt = generateFusionPrompt(faceImage, baseTexture, faceDesc);

            // Step 2: 调用通义万象生图
            return callWanxImageGen(prompt);

        } catch (Exception e) {
            log.error("纹理融合失败", e);
            throw new RuntimeException("AI 纹理融合失败: " + e.getMessage());
        } finally {
            log.info("纹理融合总耗时: {}ms", System.currentTimeMillis() - start);
        }
    }

    /** Step 1: 用 QwenVL 分析两张图并生成详细的文生图 prompt */
    private String generateFusionPrompt(byte[] faceImage, byte[] baseTexture,
                                         String faceDesc) throws Exception {
        String faceB64 = Base64.getEncoder().encodeToString(faceImage);
        String baseB64 = Base64.getEncoder().encodeToString(baseTexture);

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "image_url", "image_url",
            Map.of("url", "data:image/png;base64," + baseB64)));
        content.add(Map.of("type", "image_url", "image_url",
            Map.of("url", "data:image/png;base64," + faceB64)));
        content.add(Map.of("type", "text", "text",
            "图1是Live2D纹理底图（2048x2048），图2是角色面部参考图（" + faceDesc + "）。"
            + "请用英文描述如何将图2角色的面部五官（眼型、脸型、嘴型、眉型）自然地画在图1的面部区域上，"
            + "保持图1的整体布局、身体、头发不变，仅替换面部五官。"
            + "输出一段250字以内的英文stable diffusion prompt，用于生成融合后的完整纹理图。"
            + "重点描述：画风（anime style）、面部特征、光影色调与底图一致。"));

        Map<String, Object> body = new HashMap<>();
        body.put("model", appConfig.getQwenVl().getModel());
        body.put("messages", List.of(Map.of("role", "user", "content", content)));
        body.put("temperature", 0.5);
        body.put("max_tokens", 500);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(appConfig.getQwenVl().getApiKey());

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> resp = restTemplate.exchange(
            appConfig.getQwenVl().getApiUrl() + "/chat/completions",
            HttpMethod.POST, entity, String.class);

        JsonNode root = objectMapper.readTree(resp.getBody());
        String prompt = root.path("choices").get(0).path("message").path("content").asText();
        log.info("生成的SD prompt: {}", prompt);
        return prompt;
    }

    /** Step 2: 调用 DashScope 原生万象生图 API（异步+轮询） */
    private byte[] callWanxImageGen(String prompt) throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt + ", high quality, detailed anime texture, 2d game asset");

        Map<String, Object> params = new HashMap<>();
        params.put("size", "1024*1024");
        params.put("n", 1);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "wanx-v1");
        body.put("input", input);
        body.put("parameters", params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(appConfig.getQwenVl().getApiKey());
        headers.set("X-DashScope-Async", "enable");

        // DashScope 原生文生图 API
        String genUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/"
            + "text2image/image-synthesis";

        HttpEntity<String> entity = new HttpEntity<>(
            objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> resp = restTemplate.exchange(
            genUrl, HttpMethod.POST, entity, String.class);

        log.info("万象生图响应: {}", resp.getBody());

        JsonNode root = objectMapper.readTree(resp.getBody());
        String taskId = root.path("output").path("task_id").asText();
        if (taskId.isEmpty()) {
            throw new Exception("未获取到task_id: " + resp.getBody());
        }

        log.info("万象任务ID: {}, 等待完成...", taskId);

        // 轮询任务结果
        String taskUrl = "https://dashscope.aliyuncs.com/api/v1/tasks/" + taskId;
        HttpEntity<Void> taskEntity = new HttpEntity<>(headers);
        for (int i = 0; i < 30; i++) {
            Thread.sleep(2000);
            ResponseEntity<String> taskResp = restTemplate.exchange(
                taskUrl, HttpMethod.GET, taskEntity, String.class);
            JsonNode taskRoot = objectMapper.readTree(taskResp.getBody());
            String status = taskRoot.path("output").path("task_status").asText();
            log.info("万象状态[{}/30]: {}", i + 1, status);

            if ("SUCCEEDED".equals(status)) {
                JsonNode results = taskRoot.path("output").path("results");
                if (results.isArray() && results.size() > 0) {
                    String imageUrl = results.get(0).path("url").asText();
                    if (!imageUrl.isEmpty()) {
                        // OSS 签名URL，直接用 URLConnection 避免 header 干扰
                        java.net.URL url = new java.net.URL(imageUrl);
                        java.net.HttpURLConnection conn =
                            (java.net.HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(30000);
                        byte[] imgBytes = conn.getInputStream().readAllBytes();
                        conn.disconnect();
                        return imgBytes;
                    }
                }
                throw new Exception("万象完成但无图片");
            }
            if ("FAILED".equals(status)) {
                throw new Exception("万象生图失败: " + taskResp.getBody());
            }
        }
        throw new Exception("万象生图超时(60s)");
    }
}
