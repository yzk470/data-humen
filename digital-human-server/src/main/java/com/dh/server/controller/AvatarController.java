package com.dh.server.controller;

import com.dh.server.avatar.ModelFileService;
import com.dh.server.common.BusinessException;
import com.dh.server.common.Result;
import com.dh.server.preference.PreferencesSnapshot;
import com.dh.server.preference.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/avatar")
@RequiredArgsConstructor
public class AvatarController {

    private final ModelFileService modelFileService;
    private final UserPreferenceService userPreferenceService;

    @GetMapping("/list")
    public Result<Map<String, Object>> listAvatars() {
        try {
            List<ModelFileService.AvatarInfo> avatars = modelFileService.listAvatars();
            PreferencesSnapshot snapshot = userPreferenceService.getUserSnapshot("default-user");
            String defaultId = avatars.stream()
                .filter(it -> it.getModelPath().equals(snapshot.getDefaultModelPath()))
                .map(ModelFileService.AvatarInfo::getId)
                .findFirst()
                .orElse(avatars.isEmpty() ? "" : avatars.get(0).getId());
            return Result.ok(Map.of(
                "avatars", avatars,
                "defaultId", defaultId
            ));
        } catch (IOException e) {
            log.error("读取形象列表失败", e);
            throw new BusinessException(500, "读取形象列表失败");
        }
    }
}
