package com.dh.server.storage.service;

import com.dh.server.storage.entity.SessionEntity;
import com.dh.server.storage.mapper.SessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionStorageService {

    private final SessionMapper sessionMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "session:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    public void createSession(SessionEntity session) {
        sessionMapper.insert(session);
        String key = CACHE_PREFIX + session.getId();
        redisTemplate.opsForValue().set(key, session, CACHE_TTL);
    }

    public SessionEntity findById(String sessionId) {
        String key = CACHE_PREFIX + sessionId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return objectMapper.convertValue(cached, SessionEntity.class);
        }
        SessionEntity fromDb = sessionMapper.selectById(sessionId);
        if (fromDb != null) {
            redisTemplate.opsForValue().set(key, fromDb, CACHE_TTL);
        }
        return fromDb;
    }

    public void updateStatus(String sessionId, String status) {
        SessionEntity session = new SessionEntity();
        session.setId(sessionId);
        session.setStatus(status);
        session.setLastActiveAt(LocalDateTime.now());
        if ("CLOSED".equals(status)) {
            session.setClosedAt(LocalDateTime.now());
        }
        sessionMapper.updateById(session);
        SessionEntity updated = sessionMapper.selectById(sessionId);
        if (updated != null) {
            redisTemplate.opsForValue().set(CACHE_PREFIX + sessionId, updated, CACHE_TTL);
        }
    }

    public void touchLastActive(String sessionId) {
        updateStatus(sessionId, "ACTIVE");
    }
}
