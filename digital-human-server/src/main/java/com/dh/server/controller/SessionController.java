package com.dh.server.controller;

import com.dh.server.avatar.ModelFileService;
import com.dh.server.common.Result;
import com.dh.server.session.Session;
import com.dh.server.session.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionManager sessionManager;
    private final ModelFileService modelFileService;

    @PostMapping("/create")
    public Result<Map<String, String>> create(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        Session session = sessionManager.createSession(ip);
        return Result.ok(Map.of("sessionId", session.getSessionId()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, String>> getInfo(@PathVariable String id) {
        Session session = sessionManager.getSession(id);
        if (session == null) {
            return Result.fail(404, "会话不存在");
        }
        return Result.ok(Map.of(
            "status", session.getStatus(),
            "sessionId", session.getSessionId()
        ));
    }

    @DeleteMapping("/{id}")
    public Result<Map<String, Boolean>> close(@PathVariable String id) {
        sessionManager.closeSession(id);
        return Result.ok(Map.of("success", true));
    }

    @PutMapping("/{sessionId}/avatar")
    public Result<Map<String, String>> switchAvatar(@PathVariable String sessionId,
                                                     @RequestBody Map<String, String> body) {
        Session session = sessionManager.getSession(sessionId);
        if (session == null) {
            return Result.fail(404, "会话不存在");
        }

        String avatarId = body.get("avatarId");
        if (avatarId == null || avatarId.isEmpty()) {
            return Result.fail(400, "avatarId 不能为空");
        }

        try {
            ModelFileService.AvatarInfo info = modelFileService.getAvatarInfo(avatarId);
            return Result.ok(Map.of("modelPath", info.getModelPath()));
        } catch (IllegalArgumentException e) {
            return Result.fail(404, "形象不存在: " + avatarId);
        } catch (IOException e) {
            log.error("切换形象失败", e);
            return Result.fail(500, "切换形象失败");
        }
    }
}
