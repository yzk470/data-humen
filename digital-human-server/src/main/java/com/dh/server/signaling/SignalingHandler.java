package com.dh.server.signaling;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignalingHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = getSessionId(session);
        sessionMap.put(sessionId, session);
        log.info("信令连接建立: sessionId={}", sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SignalMessage msg = objectMapper.readValue(message.getPayload(), SignalMessage.class);
        String sessionId = getSessionId(session);
        log.debug("信令消息: type={}, sessionId={}", msg.getType(), sessionId);

        if ("offer".equals(msg.getType())) {
            SignalMessage answer = new SignalMessage();
            answer.setType("answer");
            answer.setSdp(msg.getSdp());
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(answer)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = getSessionId(session);
        sessionMap.remove(sessionId);
        log.info("信令连接断开: sessionId={}", sessionId);
    }

    private String getSessionId(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        if (query != null && query.contains("sessionId=")) {
            return query.split("sessionId=")[1].split("&")[0];
        }
        return "unknown";
    }
}
