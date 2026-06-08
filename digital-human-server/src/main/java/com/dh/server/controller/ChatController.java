package com.dh.server.controller;

import com.dh.server.common.Result;
import com.dh.server.orchestrator.ChatOrchestrator;
import com.dh.server.orchestrator.PipelineResult;
import com.dh.server.session.SessionManager;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatOrchestrator chatOrchestrator;
    private final SessionManager sessionManager;

    @PostMapping("/text")
    public Result<PipelineResult> chatText(@RequestBody ChatRequest request) {
        if (sessionManager.getSession(request.getSessionId()) == null) {
            return Result.fail(404, "会话不存在或已过期");
        }
        sessionManager.touchLastActive(request.getSessionId());
        PipelineResult result = chatOrchestrator.processText(
            request.getSessionId(), request.getText()
        );
        return Result.ok(result);
    }

    @Data
    public static class ChatRequest {
        @NotBlank(message = "sessionId 不能为空")
        private String sessionId;
        @NotBlank(message = "text 不能为空")
        private String text;
    }
}
