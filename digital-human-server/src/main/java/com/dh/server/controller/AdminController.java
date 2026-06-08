package com.dh.server.controller;

import com.dh.server.common.Result;
import com.dh.server.storage.entity.MessageEntity;
import com.dh.server.storage.service.ConfigStorageService;
import com.dh.server.storage.service.MessageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ConfigStorageService configStorageService;
    private final MessageStorageService messageStorageService;

    @GetMapping("/config/prompt")
    public Result<Map<String, String>> getPrompt() {
        String value = configStorageService.getConfigValue("system_prompt");
        return Result.ok(Map.of("system_prompt", value != null ? value : ""));
    }

    @PutMapping("/config/prompt")
    public Result<?> updatePrompt(@RequestBody Map<String, String> body) {
        configStorageService.setConfig("system_prompt", body.get("system_prompt"));
        return Result.ok();
    }

    @GetMapping("/config/tts-voice")
    public Result<Map<String, String>> getTtsVoice() {
        return Result.ok(Map.of(
            "voice_id", getOrDefault("tts_voice_id", "zh-CN-XiaoxiaoNeural"),
            "speed", getOrDefault("tts_speed", "1.0"),
            "pitch", getOrDefault("tts_pitch", "0")
        ));
    }

    @PutMapping("/config/tts-voice")
    public Result<?> updateTtsVoice(@RequestBody Map<String, String> body) {
        if (body.containsKey("voice_id")) configStorageService.setConfig("tts_voice_id", body.get("voice_id"));
        if (body.containsKey("speed")) configStorageService.setConfig("tts_speed", body.get("speed"));
        if (body.containsKey("pitch")) configStorageService.setConfig("tts_pitch", body.get("pitch"));
        return Result.ok();
    }

    @GetMapping("/config/model")
    public Result<Map<String, String>> getModel() {
        return Result.ok(Map.of(
            "live2d_model_path", getOrDefault("live2d_model_path", "/models/default.model3.json")
        ));
    }

    @PutMapping("/config/model")
    public Result<?> updateModel(@RequestBody Map<String, String> body) {
        configStorageService.setConfig("live2d_model_path", body.get("live2d_model_path"));
        return Result.ok();
    }

    @GetMapping("/logs")
    public Result<Map<String, Object>> getLogs(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String sessionId
    ) {
        if (sessionId != null && !sessionId.isEmpty()) {
            List<MessageEntity> messages = messageStorageService.getMessagesBySession(sessionId, page, size);
            return Result.ok(Map.of("list", messages, "page", page, "size", size));
        }
        return Result.ok(Map.of("list", List.of(), "page", page, "size", size));
    }

    private String getOrDefault(String key, String defaultValue) {
        String value = configStorageService.getConfigValue(key);
        return value != null ? value : defaultValue;
    }
}
