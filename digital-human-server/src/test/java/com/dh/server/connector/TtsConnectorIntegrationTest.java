package com.dh.server.connector;

import com.dh.server.config.AppConfig;
import com.dh.server.storage.service.ConfigStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TtsConnectorIntegrationTest {

    private static final String FEMALE_VOICE = "longyingxiao_v3";
    private static final String[] FEMALE_VOICE_CANDIDATES = {
        "longxiaochun",
        "longxiaoxia",
        "longxiaomei",
        "longxiaoling",
        "longyue",
        "longwan"
    };

    @Test
    @EnabledIfEnvironmentVariable(named = "ALIYUN_TTS_API_KEY", matches = ".+")
    void shouldSynthesizeAudioWithRealDashScopeApi() {
        AppConfig appConfig = new AppConfig();
        appConfig.getTts().setApiKey(System.getenv("ALIYUN_TTS_API_KEY"));
        appConfig.getTts().setApiUrl("wss://dashscope.aliyuncs.com/api-ws/v1/inference");
        appConfig.getTts().setModel("cosyvoice-v3-flash");
        appConfig.getTts().setResponseFormat("mp3");

        ConfigStorageService configStorageService = mock(ConfigStorageService.class);
        when(configStorageService.getConfigValue("tts_voice_id")).thenReturn(FEMALE_VOICE);

        TtsConnector connector = new TtsConnector(appConfig, configStorageService);
        byte[] audio = connector.execute("你好，这是一次语音合成联调测试。");

        assertTrue(audio.length > 0, "Expected synthesized audio bytes");
    }

    @Test
    @EnabledIfSystemProperty(named = "probeFemaleVoices", matches = "true")
    @EnabledIfEnvironmentVariable(named = "ALIYUN_TTS_API_KEY", matches = ".+")
    void shouldProbeFemaleVoiceCandidates() {
        for (String voice : FEMALE_VOICE_CANDIDATES) {
            AppConfig appConfig = new AppConfig();
            appConfig.getTts().setApiKey(System.getenv("ALIYUN_TTS_API_KEY"));
            appConfig.getTts().setApiUrl("wss://dashscope.aliyuncs.com/api-ws/v1/inference");
            appConfig.getTts().setModel("cosyvoice-v3-flash");
            appConfig.getTts().setResponseFormat("mp3");

            ConfigStorageService configStorageService = mock(ConfigStorageService.class);
            when(configStorageService.getConfigValue("tts_voice_id")).thenReturn(voice);

            TtsConnector connector = new TtsConnector(appConfig, configStorageService);
            byte[] audio = connector.execute("你好，这是女生音色探测。");
            System.out.println("voice=" + voice + ", bytes=" + audio.length);
        }
    }
}
