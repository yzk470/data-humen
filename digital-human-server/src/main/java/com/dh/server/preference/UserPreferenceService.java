package com.dh.server.preference;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dh.server.storage.entity.UserPreferenceEntity;
import com.dh.server.storage.mapper.UserPreferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceMapper userPreferenceMapper;
    private final PreferenceConfigService preferenceConfigService;

    public PreferencesSnapshot getUserSnapshot(String userId) {
        PreferencesSnapshot admin = preferenceConfigService.getAdminSnapshot();
        UserPreferenceEntity entity = findByUserId(userId);

        String currentVoiceId = entity != null && contains(admin.getVoiceOptions(), entity.getTtsVoiceId())
            ? entity.getTtsVoiceId()
            : admin.getDefaultVoiceId();
        String currentModelPath = entity != null && contains(admin.getModelOptions(), entity.getLive2dModelPath())
            ? entity.getLive2dModelPath()
            : admin.getDefaultModelPath();

        admin.setCurrentVoiceId(currentVoiceId);
        admin.setCurrentModelPath(currentModelPath);
        return admin;
    }

    public void saveUserPreference(String userId, String voiceId, String modelPath) {
        PreferencesSnapshot admin = preferenceConfigService.getAdminSnapshot();
        if (!contains(admin.getVoiceOptions(), voiceId)) {
            throw new IllegalArgumentException("voiceId is not in configured pool");
        }
        if (!contains(admin.getModelOptions(), modelPath)) {
            throw new IllegalArgumentException("modelPath is not in configured pool");
        }

        UserPreferenceEntity entity = findByUserId(userId);
        if (entity == null) {
            entity = new UserPreferenceEntity();
            entity.setUserId(userId);
            entity.setTtsVoiceId(voiceId);
            entity.setLive2dModelPath(modelPath);
            userPreferenceMapper.insert(entity);
            return;
        }
        entity.setTtsVoiceId(voiceId);
        entity.setLive2dModelPath(modelPath);
        userPreferenceMapper.updateById(entity);
    }

    private UserPreferenceEntity findByUserId(String userId) {
        return userPreferenceMapper.selectOne(
            new LambdaQueryWrapper<UserPreferenceEntity>().eq(UserPreferenceEntity::getUserId, userId)
        );
    }

    private boolean contains(List<PreferenceOption> options, String value) {
        return options.stream().anyMatch(it -> it.getValue().equals(value));
    }
}
