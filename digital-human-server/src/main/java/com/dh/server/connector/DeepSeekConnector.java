package com.dh.server.connector;

import com.dh.server.config.AppConfig;
import com.dh.server.session.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Slf4j
@Component
public class DeepSeekConnector implements Connector<DeepSeekConnector.DeepSeekInput, String> {

    private final AppConfig appConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;

    public DeepSeekConnector(AppConfig appConfig) {
        this.appConfig = appConfig;
        this.restTemplate = new RestTemplate();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate.setRequestFactory(factory);
    }

    @Data
    @Builder
    public static class DeepSeekInput {
        private String systemPrompt;
        private String userText;
        private List<Message> history;
    }

    @Override
    public String execute(DeepSeekInput input) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", input.getSystemPrompt()));

        if (input.getHistory() != null) {
            for (Message msg : input.getHistory()) {
                messages.add(Map.of(
                    "role", msg.getRole().toLowerCase(),
                    "content", msg.getText()
                ));
            }
        }
        messages.add(Map.of("role", "user", "content", input.getUserText()));

        Map<String, Object> body = new HashMap<>();
        body.put("model", appConfig.getDeepseek().getModel());
        body.put("messages", messages);
        body.put("temperature", 0.7);
        body.put("max_tokens", 512);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(appConfig.getDeepseek().getApiKey());

        try {
            String requestBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<DeepSeekResponse> response = restTemplate.exchange(
                appConfig.getDeepseek().getApiUrl(),
                HttpMethod.POST,
                entity,
                DeepSeekResponse.class
            );
            if (response.getBody() != null && response.getBody().getChoices() != null
                && !response.getBody().getChoices().isEmpty()) {
                return response.getBody().getChoices().get(0).getMessage().getContent();
            }
            throw new RuntimeException("DeepSeek 返回空响应");
        } catch (Exception e) {
            log.error("DeepSeek API 调用失败", e);
            throw new RuntimeException("AI 服务暂时不可用，请稍后重试");
        }
    }

    @Data
    private static class DeepSeekResponse {
        private List<Choice> choices;

        @Data
        public static class Choice {
            private MessageDetail message;
        }

        @Data
        public static class MessageDetail {
            private String content;
        }
    }
}
