package com.dh.server.orchestrator;

import com.dh.server.connector.DeepSeekConnector;
import com.dh.server.connector.TtsConnector;
import com.dh.server.emotion.EmotionCalculator;
import com.dh.server.emotion.EmotionResult;
import com.dh.server.emotion.EmotionToLive2DParams;
import com.dh.server.session.Message;
import com.dh.server.storage.entity.MessageEntity;
import com.dh.server.storage.service.ConfigStorageService;
import com.dh.server.storage.service.MessageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatOrchestrator {

    private final DeepSeekConnector deepSeekConnector;
    private final TtsConnector ttsConnector;
    private final EmotionCalculator emotionCalculator;
    private final EmotionToLive2DParams emotionToParams;
    private final ConfigStorageService configStorageService;
    private final MessageStorageService messageStorageService;

    public PipelineResult processText(String sessionId, String userText) {
        saveMessage(sessionId, "USER", userText, null);

        String systemPrompt = configStorageService.getConfigValue("system_prompt");
        if (systemPrompt == null) {
            systemPrompt = "你是一个友好的数字人助手。";
        }

        List<MessageEntity> historyEntities = messageStorageService.getRecentMessages(sessionId, 10);
        List<Message> history = historyEntities.stream()
            .map(e -> Message.builder()
                .role(e.getRole())
                .text(e.getText())
                .build())
            .collect(Collectors.toList());

        String llmReply;
        try {
            DeepSeekConnector.DeepSeekInput llmInput = DeepSeekConnector.DeepSeekInput.builder()
                .systemPrompt(systemPrompt)
                .userText(userText)
                .history(history)
                .build();
            llmReply = deepSeekConnector.execute(llmInput);
        } catch (RuntimeException e) {
            log.error("DeepSeek API 调用失败，返回降级回复", e);
            llmReply = "抱歉，AI 服务暂时不可用，请稍后再试。[EMOTION:sorry]";
        }

        EmotionResult emotionResult = emotionCalculator.calculate(llmReply);
        String cleanText = emotionCalculator.removeEmotionTag(llmReply);
        String audioBase64 = ttsConnector.executeAsBase64(cleanText);

        saveMessage(sessionId, "ASSISTANT", cleanText, emotionResult.getLabel().getLabel());

        return PipelineResult.builder()
            .text(cleanText)
            .emotion(emotionResult.getLabel().getLabel())
            .animationParams(emotionToParams.mapToParams(emotionResult.getLabel()))
            .audioBase64(audioBase64)
            .build();
    }

    public PipelineResult processVoice(String sessionId, byte[] audioBytes) {
        log.info("语音流水线待 WebRTC 集成阶段实现");
        return PipelineResult.builder().build();
    }

    private void saveMessage(String sessionId, String role, String text, String emotion) {
        MessageEntity entity = new MessageEntity();
        entity.setSessionId(sessionId);
        entity.setRole(role);
        entity.setText(text);
        entity.setEmotion(emotion);
        messageStorageService.saveMessage(entity);
    }
}
