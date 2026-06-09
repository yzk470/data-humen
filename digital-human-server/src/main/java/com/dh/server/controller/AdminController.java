package com.dh.server.controller;

import com.dh.server.avatar.AvatarService;
import com.dh.server.avatar.ModelFileService;
import com.dh.server.common.BusinessException;
import com.dh.server.common.Result;
import com.dh.server.storage.entity.MessageEntity;
import com.dh.server.storage.service.ConfigStorageService;
import com.dh.server.storage.service.MessageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ConfigStorageService configStorageService;
    private final MessageStorageService messageStorageService;
    private final AvatarService avatarService;
    private final ModelFileService modelFileService;

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

    // ========== 形象管理 API ==========

    @PostMapping("/avatar/upload")
    public Result<ModelFileService.AvatarInfo> uploadAvatar(
            @RequestParam("image") MultipartFile image,
            @RequestParam("name") String name,
            @RequestParam("cropX") int cropX,
            @RequestParam("cropY") int cropY,
            @RequestParam("cropW") int cropW,
            @RequestParam("cropH") int cropH) {

        if (image.isEmpty()) {
            throw new BusinessException(400, "请选择图片文件");
        }

        String contentType = image.getContentType();
        if (contentType == null ||
            (!contentType.equals("image/png") && !contentType.equals("image/jpeg"))) {
            throw new BusinessException(400, "仅支持 PNG/JPG 格式");
        }

        if (image.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException(400, "图片大小不能超过 10MB");
        }

        try {
            byte[] imageBytes = image.getBytes();
            return Result.ok(avatarService.generateAvatar(
                imageBytes, name, cropX, cropY, cropW, cropH));
        } catch (IOException e) {
            log.error("形象生成失败", e);
            throw new BusinessException(500, "形象生成失败: " + e.getMessage());
        }
    }

    @GetMapping("/avatar/list")
    public Result<Map<String, Object>> listAvatars() {
        try {
            List<ModelFileService.AvatarInfo> avatars = modelFileService.listAvatars();
            String defaultId = avatars.isEmpty() ? "" : avatars.get(0).getId();
            return Result.ok(Map.of(
                "avatars", avatars,
                "defaultId", defaultId
            ));
        } catch (IOException e) {
            log.error("读取形象列表失败", e);
            throw new BusinessException(500, "读取形象列表失败");
        }
    }

    @DeleteMapping("/avatar/{id}")
    public Result<Map<String, Boolean>> deleteAvatar(@PathVariable String id) {
        try {
            List<ModelFileService.AvatarInfo> avatars = modelFileService.listAvatars();
            if (!avatars.isEmpty() && avatars.get(0).getId().equals(id)) {
                throw new BusinessException(400, "不能删除默认形象");
            }
            modelFileService.deleteAvatar(id);
            return Result.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(404, e.getMessage());
        } catch (IOException e) {
            log.error("删除形象失败", e);
            throw new BusinessException(500, "删除形象失败");
        }
    }
}
