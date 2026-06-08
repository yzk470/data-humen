package com.dh.server.storage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dh.server.storage.entity.MessageEntity;
import com.dh.server.storage.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageStorageService {

    private final MessageMapper messageMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String HISTORY_CACHE_PREFIX = "session:history:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final int RECENT_COUNT = 10;

    public void saveMessage(MessageEntity message) {
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        String key = HISTORY_CACHE_PREFIX + message.getSessionId();
        @SuppressWarnings("unchecked")
        List<MessageEntity> cached = (List<MessageEntity>) redisTemplate.opsForValue().get(key);
        if (cached == null) {
            cached = new ArrayList<>();
        }
        cached.add(message);
        if (cached.size() > RECENT_COUNT) {
            cached = cached.subList(cached.size() - RECENT_COUNT, cached.size());
        }
        redisTemplate.opsForValue().set(key, cached, CACHE_TTL);
    }

    public List<MessageEntity> getRecentMessages(String sessionId, int count) {
        String key = HISTORY_CACHE_PREFIX + sessionId;
        @SuppressWarnings("unchecked")
        List<MessageEntity> cached = (List<MessageEntity>) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (cached.size() <= count) return cached;
            return cached.subList(cached.size() - count, cached.size());
        }
        return loadRecentFromDb(sessionId, count);
    }

    private List<MessageEntity> loadRecentFromDb(String sessionId, int count) {
        LambdaQueryWrapper<MessageEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageEntity::getSessionId, sessionId)
               .orderByDesc(MessageEntity::getCreatedAt)
               .last("LIMIT " + count);
        List<MessageEntity> fromDb = messageMapper.selectList(wrapper);
        String key = HISTORY_CACHE_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, fromDb, CACHE_TTL);
        return fromDb;
    }

    public List<MessageEntity> getMessagesBySession(String sessionId, int page, int size) {
        LambdaQueryWrapper<MessageEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageEntity::getSessionId, sessionId)
               .orderByAsc(MessageEntity::getCreatedAt);
        int offset = (page - 1) * size;
        wrapper.last("LIMIT " + offset + "," + size);
        return messageMapper.selectList(wrapper);
    }
}
