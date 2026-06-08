package com.dh.server.controller;

import com.dh.server.common.Result;
import com.dh.server.session.Session;
import com.dh.server.session.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionManager sessionManager;

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
}
