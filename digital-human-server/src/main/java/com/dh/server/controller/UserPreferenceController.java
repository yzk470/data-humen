package com.dh.server.controller;

import com.dh.server.common.Result;
import com.dh.server.preference.PreferencesSnapshot;
import com.dh.server.preference.UserPreferenceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private static final String DEFAULT_USER_ID = "default-user";

    private final UserPreferenceService userPreferenceService;

    @GetMapping
    public Result<PreferencesSnapshot> getPreferences() {
        return Result.ok(userPreferenceService.getUserSnapshot(DEFAULT_USER_ID));
    }

    @PutMapping
    public Result<?> updatePreferences(@RequestBody UpdateRequest request) {
        userPreferenceService.saveUserPreference(DEFAULT_USER_ID, request.getVoiceId(), request.getModelPath());
        return Result.ok();
    }

    @Data
    public static class UpdateRequest {
        private String voiceId;
        private String modelPath;
    }
}
