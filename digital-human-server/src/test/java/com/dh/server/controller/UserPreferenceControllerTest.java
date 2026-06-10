package com.dh.server.controller;

import com.dh.server.preference.PreferenceOption;
import com.dh.server.preference.PreferencesSnapshot;
import com.dh.server.preference.UserPreferenceService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserPreferenceControllerTest {

    @Test
    void shouldReturnCurrentUserPreferences() {
        UserPreferenceService service = mock(UserPreferenceService.class);
        when(service.getUserSnapshot("default-user")).thenReturn(PreferencesSnapshot.builder()
            .voiceOptions(List.of(new PreferenceOption("莹晓", "longyingxiao_v3")))
            .modelOptions(List.of(new PreferenceOption("默认 Haru", "/models/generated/avatar_default/Haru.model3.json")))
            .defaultVoiceId("longyingxiao_v3")
            .defaultModelPath("/models/generated/avatar_default/Haru.model3.json")
            .currentVoiceId("longyingxiao_v3")
            .currentModelPath("/models/generated/avatar_default/Haru.model3.json")
            .build());

        UserPreferenceController controller = new UserPreferenceController(service);

        assertEquals("longyingxiao_v3", controller.getPreferences().getData().getCurrentVoiceId());
    }
}
