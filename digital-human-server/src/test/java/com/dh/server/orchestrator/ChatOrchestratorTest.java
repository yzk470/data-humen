package com.dh.server.orchestrator;

import com.dh.server.connector.DeepSeekConnector;
import com.dh.server.connector.TtsConnector;
import com.dh.server.emotion.EmotionCalculator;
import com.dh.server.emotion.EmotionToLive2DParams;
import com.dh.server.preference.PreferenceOption;
import com.dh.server.preference.PreferencesSnapshot;
import com.dh.server.preference.UserPreferenceService;
import com.dh.server.storage.entity.MessageEntity;
import com.dh.server.storage.service.ConfigStorageService;
import com.dh.server.storage.service.MessageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatOrchestratorTest {

    private DeepSeekConnector deepSeekConnector;
    private TtsConnector ttsConnector;
    private EmotionCalculator emotionCalculator;
    private EmotionToLive2DParams emotionToParams;
    private ConfigStorageService configStorageService;
    private MessageStorageService messageStorageService;
    private UserPreferenceService userPreferenceService;
    private ChatOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        deepSeekConnector = mock(DeepSeekConnector.class);
        ttsConnector = mock(TtsConnector.class);
        emotionCalculator = new EmotionCalculator();
        emotionToParams = new EmotionToLive2DParams();
        configStorageService = mock(ConfigStorageService.class);
        messageStorageService = mock(MessageStorageService.class);
        userPreferenceService = mock(UserPreferenceService.class);

        when(userPreferenceService.getUserSnapshot("default-user")).thenReturn(PreferencesSnapshot.builder()
            .currentVoiceId("longyingxiao_v3")
            .currentModelPath("/models/generated/avatar_default/Haru.model3.json")
            .build());

        orchestrator = new ChatOrchestrator(
            deepSeekConnector, ttsConnector, emotionCalculator,
            emotionToParams, configStorageService, messageStorageService,
            userPreferenceService
        );
    }

    @Test
    void shouldCompleteTextPipelineSuccessfully() {
        when(configStorageService.getConfigValue("system_prompt"))
            .thenReturn("你是一个友好的助手。");
        when(messageStorageService.getRecentMessages("session-1", 10))
            .thenReturn(List.of());
        when(deepSeekConnector.execute(any()))
            .thenReturn("你好！有什么可以帮你的吗？[EMOTION:happy]");
        when(ttsConnector.executeAsBase64(anyString(), anyString()))
            .thenReturn("base64audio==");

        PipelineResult result = orchestrator.processText("default-user", "session-1", "你好");

        assertNotNull(result);
        assertEquals("你好！有什么可以帮你的吗？", result.getText());
        assertEquals("happy", result.getEmotion());
        assertEquals("base64audio==", result.getAudioBase64());
        assertFalse(result.getAnimationParams().isEmpty());
        assertEquals(0.8, result.getAnimationParams().get("ParamMouthForm"), 0.01);

        verify(messageStorageService, times(2)).saveMessage(any(MessageEntity.class));
    }

    @Test
    void shouldUseCurrentUserVoicePreferenceForTts() {
        when(configStorageService.getConfigValue("system_prompt"))
            .thenReturn("你是助手。");
        when(messageStorageService.getRecentMessages("session-1", 10))
            .thenReturn(List.of());
        when(deepSeekConnector.execute(any()))
            .thenReturn("你好[EMOTION:happy]");
        when(ttsConnector.executeAsBase64(anyString(), anyString()))
            .thenReturn("base64audio==");

        orchestrator.processText("default-user", "session-1", "你好");

        verify(ttsConnector).executeAsBase64("你好", "longyingxiao_v3");
    }

    @Test
    void shouldHandleLlmReplyWithoutEmotionTag() {
        when(configStorageService.getConfigValue("system_prompt"))
            .thenReturn("你是一个助手。");
        when(messageStorageService.getRecentMessages("s1", 10))
            .thenReturn(List.of());
        when(deepSeekConnector.execute(any()))
            .thenReturn("今天是星期一。");
        when(ttsConnector.executeAsBase64(anyString(), anyString()))
            .thenReturn("audio");

        PipelineResult result = orchestrator.processText("default-user", "s1", "今天星期几？");

        assertEquals("neutral", result.getEmotion());
        assertEquals("今天是星期一。", result.getText());
        assertEquals(0.0, result.getAnimationParams().get("ParamMouthForm"), 0.01);
    }
}
