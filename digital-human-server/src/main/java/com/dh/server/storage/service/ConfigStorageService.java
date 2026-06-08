package com.dh.server.storage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dh.server.storage.entity.DhConfigEntity;
import com.dh.server.storage.mapper.DhConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConfigStorageService {

    private final DhConfigMapper configMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_KEY = "dh:config";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    public String getConfigValue(String key) {
        return getAllConfigs().get(key);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getAllConfigs() {
        Map<String, String> cached = (Map<String, String>) redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        return refreshCache();
    }

    public void setConfig(String configKey, String configValue) {
        DhConfigEntity existing = configMapper.selectOne(
            new LambdaQueryWrapper<DhConfigEntity>().eq(DhConfigEntity::getConfigKey, configKey)
        );
        if (existing != null) {
            existing.setConfigValue(configValue);
            configMapper.updateById(existing);
        } else {
            DhConfigEntity newConfig = new DhConfigEntity();
            newConfig.setConfigKey(configKey);
            newConfig.setConfigValue(configValue);
            configMapper.insert(newConfig);
        }
        refreshCache();
    }

    private Map<String, String> refreshCache() {
        Map<String, String> configs = configMapper.selectList(null).stream()
            .collect(Collectors.toMap(DhConfigEntity::getConfigKey, DhConfigEntity::getConfigValue));
        redisTemplate.opsForValue().set(CACHE_KEY, configs, CACHE_TTL);
        return configs;
    }
}
