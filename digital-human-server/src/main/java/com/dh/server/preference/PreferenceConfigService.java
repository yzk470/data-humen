package com.dh.server.preference;

import com.dh.server.storage.service.ConfigStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PreferenceConfigService {

    private final ConfigStorageService configStorageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PreferencesSnapshot getAdminSnapshot() {
        return PreferencesSnapshot.builder()
            .voiceOptions(readOptions("tts_voice_options"))
            .modelOptions(readOptions("live2d_model_options"))
            .defaultVoiceId(configStorageService.getConfigValue("default_tts_voice_id"))
            .defaultModelPath(configStorageService.getConfigValue("default_live2d_model_path"))
            .build();
    }

    public void saveAdminSnapshot(PreferencesSnapshot snapshot) {
        validateAdminSnapshot(snapshot);
        writeOptions("tts_voice_options", snapshot.getVoiceOptions());
        writeOptions("live2d_model_options", snapshot.getModelOptions());
        configStorageService.setConfig("default_tts_voice_id", snapshot.getDefaultVoiceId());
        configStorageService.setConfig("default_live2d_model_path", snapshot.getDefaultModelPath());
    }

    void validateAdminSnapshot(PreferencesSnapshot snapshot) {
        validateUnique(snapshot.getVoiceOptions(), "voice");
        validateUnique(snapshot.getModelOptions(), "model");
        if (snapshot.getVoiceOptions().stream().noneMatch(it -> it.getValue().equals(snapshot.getDefaultVoiceId()))) {
            throw new IllegalArgumentException("default voice must exist in voice options");
        }
        if (snapshot.getModelOptions().stream().noneMatch(it -> it.getValue().equals(snapshot.getDefaultModelPath()))) {
            throw new IllegalArgumentException("default model must exist in model options");
        }
    }

    public List<PreferenceOption> readOptions(String key) {
        try {
            String json = configStorageService.getConfigValue(key);
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse config " + key, e);
        }
    }

    private void writeOptions(String key, List<PreferenceOption> options) {
        try {
            configStorageService.setConfig(key, objectMapper.writeValueAsString(options));
        } catch (Exception e) {
            throw new IllegalStateException("failed to write config " + key, e);
        }
    }

    private void validateUnique(List<PreferenceOption> options, String name) {
        Set<String> values = new HashSet<>();
        for (PreferenceOption option : options) {
            if (option.getLabel() == null || option.getLabel().isBlank() || option.getValue() == null || option.getValue().isBlank()) {
                throw new IllegalArgumentException(name + " option requires label and value");
            }
            if (!values.add(option.getValue())) {
                throw new IllegalArgumentException("duplicate " + name + " option value: " + option.getValue());
            }
        }
    }
}
