package com.dh.server.connector;

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.utils.Constants;
import com.dh.server.config.AppConfig;
import com.dh.server.storage.service.ConfigStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class TtsConnector implements Connector<String, byte[]> {

    private static final String DEFAULT_VOICE = "longyingxiao_v3";
    private static final String LEGACY_VOICE = "longxiaochun";
    private static final String DEFAULT_WS_URL = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
    private static final String DEFAULT_MODEL = "cosyvoice-v3-flash";

    private final AppConfig appConfig;
    private final ConfigStorageService configStorageService;

    @Override
    public byte[] execute(String text) {
        return execute(text, null);
    }

    public String executeAsBase64(String text) {
        return executeAsBase64(text, null);
    }

    public String executeAsBase64(String text, String preferredVoiceId) {
        byte[] audio = execute(text, preferredVoiceId);
        if (audio.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(audio);
    }

    public byte[] execute(String text, String preferredVoiceId) {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }

        String apiKey = appConfig.getTts().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Aliyun TTS api key is not configured. Returning empty audio.");
            return new byte[0];
        }

        String model = isBlank(appConfig.getTts().getModel()) ? DEFAULT_MODEL : appConfig.getTts().getModel();
        String websocketUrl = isBlank(appConfig.getTts().getApiUrl()) ? DEFAULT_WS_URL : appConfig.getTts().getApiUrl();
        LinkedHashSet<String> voices = new LinkedHashSet<>();
        if (preferredVoiceId != null && !preferredVoiceId.isBlank()) {
            voices.add(preferredVoiceId.trim());
        }
        voices.addAll(buildVoiceCandidates());

        int attempt = 0;
        String lastError = "no attempts executed";

        for (String voice : voices) {
            attempt++;
            try {
                byte[] audio = synthesize(apiKey, websocketUrl, model, voice, text);
                if (audio.length > 0) {
                    log.info("Aliyun TTS succeeded on attempt {} with model={} voice={}", attempt, model, voice);
                    return audio;
                }
                lastError = "no playable audio returned";
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn(
                    "Aliyun TTS attempt {} failed with model={} voice={} url={}: {}",
                    attempt,
                    model,
                    voice,
                    websocketUrl,
                    e.getMessage(),
                    e
                );
            }
        }

        log.warn("Aliyun TTS failed after {} attempts. Last error: {}", attempt, lastError);
        return new byte[0];
    }

    private byte[] synthesize(String apiKey, String websocketUrl, String model, String voice, String text) throws Exception {
        Constants.baseWebsocketApiUrl = websocketUrl;

        SpeechSynthesisParam param = SpeechSynthesisParam.builder()
            .apiKey(apiKey)
            .model(model)
            .voice(voice)
            .build();

        SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, null);
        try {
            ByteBuffer audio = synthesizer.call(text);
            if (audio == null) {
                return new byte[0];
            }
            byte[] bytes = toByteArray(audio);
            if (bytes.length == 0) {
                log.warn(
                    "Aliyun TTS returned empty audio with requestId={} firstPackageDelay={}",
                    synthesizer.getLastRequestId(),
                    synthesizer.getFirstPackageDelay()
                );
            }
            return bytes;
        } finally {
            try {
                if (synthesizer.getDuplexApi() != null) {
                    synthesizer.getDuplexApi().close(1000, "bye");
                }
            } catch (Exception closeError) {
                log.debug("Failed to close Aliyun TTS websocket cleanly", closeError);
            }
        }
    }

    private Set<String> buildVoiceCandidates() {
        LinkedHashSet<String> voices = new LinkedHashSet<>();

        String configuredVoice = configStorageService.getConfigValue("tts_voice_id");
        if (!isBlank(configuredVoice)) {
            voices.add(configuredVoice.trim());
        }

        if (voices.contains(LEGACY_VOICE)) {
            voices.add(DEFAULT_VOICE);
        }

        voices.add(DEFAULT_VOICE);
        return voices;
    }

    private byte[] toByteArray(ByteBuffer buffer) {
        ByteBuffer copy = buffer.slice();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
