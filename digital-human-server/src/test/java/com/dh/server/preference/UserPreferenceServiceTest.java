package com.dh.server.preference;

import com.dh.server.storage.entity.UserPreferenceEntity;
import com.dh.server.storage.mapper.UserPreferenceMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserPreferenceServiceTest {

    @Test
    void shouldFallbackToDefaultsWhenUserPreferenceMissing() {
        UserPreferenceMapper mapper = mock(UserPreferenceMapper.class);
        PreferenceConfigService configService = mock(PreferenceConfigService.class);
        when(mapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(null);
        when(configService.getAdminSnapshot()).thenReturn(PreferencesSnapshot.builder()
            .voiceOptions(List.of(new PreferenceOption("莹晓", "longyingxiao_v3")))
            .modelOptions(List.of(new PreferenceOption("默认 Haru", "/models/generated/avatar_default/Haru.model3.json")))
            .defaultVoiceId("longyingxiao_v3")
            .defaultModelPath("/models/generated/avatar_default/Haru.model3.json")
            .build());

        UserPreferenceService service = new UserPreferenceService(mapper, configService);

        PreferencesSnapshot snapshot = service.getUserSnapshot("default-user");

        assertEquals("longyingxiao_v3", snapshot.getCurrentVoiceId());
        assertEquals("/models/generated/avatar_default/Haru.model3.json", snapshot.getCurrentModelPath());
    }

    @Test
    void shouldRejectValueOutsideConfiguredPool() {
        UserPreferenceMapper mapper = mock(UserPreferenceMapper.class);
        PreferenceConfigService configService = mock(PreferenceConfigService.class);
        when(configService.getAdminSnapshot()).thenReturn(PreferencesSnapshot.builder()
            .voiceOptions(List.of(new PreferenceOption("莹晓", "longyingxiao_v3")))
            .modelOptions(List.of(new PreferenceOption("默认 Haru", "/models/generated/avatar_default/Haru.model3.json")))
            .defaultVoiceId("longyingxiao_v3")
            .defaultModelPath("/models/generated/avatar_default/Haru.model3.json")
            .build());

        UserPreferenceService service = new UserPreferenceService(mapper, configService);

        assertThrows(IllegalArgumentException.class, () ->
            service.saveUserPreference("default-user", "bad-voice", "/models/generated/avatar_default/Haru.model3.json")
        );
    }
}
