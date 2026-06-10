package com.dh.server.storage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dh.server.storage.entity.MessageEntity;
import com.dh.server.storage.mapper.MessageMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    private static final String HISTORY_CACHE_PREFIX = "session:history:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final int RECENT_COUNT = 10;

    public void saveMessage(MessageEntity message) {
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        String key = HISTORY_CACHE_PREFIX + message.getSessionId();
        Object cached = redisTemplate.opsForValue().get(key);
        List<MessageEntity> list;
        if (cached != null) {
            list = objectMapper.convertValue(cached,
                objectMapper.getTypeFactory().constructCollectionType(List.class, MessageEntity.class));
        } else {
            list = new ArrayList<>();
        }
        list.add(message);
        if (list.size() > RECENT_COUNT) {
            list = list.subList(list.size() - RECENT_COUNT, list.size());
        }
        redisTemplate.opsForValue().set(key, list, CACHE_TTL);
    }

    public List<MessageEntity> getRecentMessages(String sessionId, int count) {
        String key = HISTORY_CACHE_PREFIX + sessionId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            List<MessageEntity> list = objectMapper.convertValue(cached,
                objectMapper.getTypeFactory().constructCollectionType(List.class, MessageEntity.class));
            if (list.size() <= count) return list;
            return list.subList(list.size() - count, list.size());
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
