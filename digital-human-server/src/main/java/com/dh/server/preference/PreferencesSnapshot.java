package com.dh.server.preference;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferencesSnapshot {
    private List<PreferenceOption> voiceOptions;
    private List<PreferenceOption> modelOptions;
    private String defaultVoiceId;
    private String defaultModelPath;
    private String currentVoiceId;
    private String currentModelPath;
}
