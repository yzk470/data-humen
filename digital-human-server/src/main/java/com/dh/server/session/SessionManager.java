package com.dh.server.session;

import com.dh.server.storage.entity.SessionEntity;
import com.dh.server.storage.service.SessionStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SessionManager {

    private final SessionStorageService sessionStorageService;

    public Session createSession(String userIp) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        SessionEntity entity = new SessionEntity();
        entity.setId(sessionId);
        entity.setStatus("ACTIVE");
        entity.setUserIp(userIp);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setLastActiveAt(LocalDateTime.now());
        sessionStorageService.createSession(entity);

        return Session.builder()
            .sessionId(sessionId)
            .status("ACTIVE")
            .createdAt(entity.getCreatedAt().toInstant(ZoneOffset.ofHours(8)))
            .lastActiveAt(entity.getLastActiveAt().toInstant(ZoneOffset.ofHours(8)))
            .build();
    }

    public Session getSession(String sessionId) {
        SessionEntity entity = sessionStorageService.findById(sessionId);
        if (entity == null) return null;
        return Session.builder()
            .sessionId(entity.getId())
            .status(entity.getStatus())
            .build();
    }

    public void closeSession(String sessionId) {
        sessionStorageService.updateStatus(sessionId, "CLOSED");
    }

    public void touchLastActive(String sessionId) {
        sessionStorageService.touchLastActive(sessionId);
    }
}
