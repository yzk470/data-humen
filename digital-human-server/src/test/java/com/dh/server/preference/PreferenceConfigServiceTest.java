package com.dh.server.preference;

import com.dh.server.storage.service.ConfigStorageService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreferenceConfigServiceTest {

    @Test
    void shouldParseVoiceAndModelOptionsFromJsonConfig() {
        ConfigStorageService configStorageService = mock(ConfigStorageService.class);
        when(configStorageService.getConfigValue("tts_voice_options"))
            .thenReturn("[{\"label\":\"莹晓\",\"value\":\"longyingxiao_v3\"}]");
        when(configStorageService.getConfigValue("live2d_model_options"))
            .thenReturn("[{\"label\":\"默认 Haru\",\"value\":\"/models/generated/avatar_default/Haru.model3.json\"}]");
        when(configStorageService.getConfigValue("default_tts_voice_id"))
            .thenReturn("longyingxiao_v3");
        when(configStorageService.getConfigValue("default_live2d_model_path"))
            .thenReturn("/models/generated/avatar_default/Haru.model3.json");

        PreferenceConfigService service = new PreferenceConfigService(configStorageService);

        PreferencesSnapshot snapshot = service.getAdminSnapshot();

        assertEquals("longyingxiao_v3", snapshot.getDefaultVoiceId());
        assertEquals("/models/generated/avatar_default/Haru.model3.json", snapshot.getDefaultModelPath());
        assertEquals(1, snapshot.getVoiceOptions().size());
        assertEquals(1, snapshot.getModelOptions().size());
    }

    @Test
    void shouldRejectDefaultVoiceOutsideOptions() {
        ConfigStorageService configStorageService = mock(ConfigStorageService.class);
        PreferenceConfigService service = new PreferenceConfigService(configStorageService);

        PreferencesSnapshot request = PreferencesSnapshot.builder()
            .voiceOptions(java.util.List.of(new PreferenceOption("莹晓", "longyingxiao_v3")))
            .modelOptions(java.util.List.of(new PreferenceOption("默认 Haru", "/models/generated/avatar_default/Haru.model3.json")))
            .defaultVoiceId("missing_voice")
            .defaultModelPath("/models/generated/avatar_default/Haru.model3.json")
            .build();

        assertThrows(IllegalArgumentException.class, () -> service.validateAdminSnapshot(request));
    }
}
